package services;

import data.BookRepository;
import data.LoanRepository;
import data.MemberRepository;
import data.ReservationRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import model.Book;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.Reservation;
import model.ReservationStatus;
import model.User;

/** Owns reservation validation, queue order, holds and expiry. */
public final class ReservationService {
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final ChargeService charges;
    private final SettingsService settings;
    private final PermissionPolicy permissions;
    private final ReservationEventListener events;

    public ReservationService(BookRepository books, MemberRepository members, LoanRepository loans,
            ReservationRepository reservations, ChargeService charges, SettingsService settings) {
        this(books, members, loans, reservations, charges, settings, new PermissionPolicy(), ReservationEventListener.NO_OP);
    }
    public ReservationService(BookRepository books, MemberRepository members, LoanRepository loans,
            ReservationRepository reservations, ChargeService charges, SettingsService settings,
            PermissionPolicy permissions, ReservationEventListener events) {
        this.books=Objects.requireNonNull(books); this.members=Objects.requireNonNull(members); this.loans=Objects.requireNonNull(loans);
        this.reservations=Objects.requireNonNull(reservations); this.charges=Objects.requireNonNull(charges); this.settings=Objects.requireNonNull(settings);
        this.permissions=Objects.requireNonNull(permissions); this.events=Objects.requireNonNull(events);
    }

    public Reservation reserve(User actor, String memberId, String bookId) throws IOException {
        requireCirculation(actor);
        LocalDate today=LocalDate.now(); Member member=findMember(memberId); Book book=findBook(bookId); List<String> failures=new ArrayList<>();
        if(member.status()!=MemberStatus.ACTIVE) failures.add("Member is not Active.");
        if(member.expiredOn(today)) failures.add("Membership expired on "+member.expiryDate()+".");
        if(lendableCopies(bookId)>0) failures.add("Copies are available. Lend it instead.");
        if(loans.findAll().stream().anyMatch(l->l.memberId().equals(memberId)&&l.bookId().equals(bookId)&&l.status()==LoanStatus.ACTIVE)) failures.add("Member already holds an active loan for this book.");
        List<Reservation> mine=openForMember(memberId); if(mine.stream().anyMatch(r->r.bookId().equals(bookId))) failures.add("Member already has a reservation for this book.");
        if(mine.size()>=settings.getInt("reservation.maxPerMember")) failures.add("Reservation limit reached.");
        if(!failures.isEmpty()) throw new IllegalArgumentException(String.join("\n",failures));
        String feeId=""; var fee=settings.getMoney("fee.reservation");
        if(fee.signum()>0) feeId=charges.add(memberId,"",model.ChargeType.RESERVATION,"Reservation: "+book.title(),fee).chargeId();
        Reservation created=new Reservation(UUID.randomUUID().toString(),book.bookId(),book.title(),memberId,LocalDateTime.now(),ReservationStatus.PENDING,null,null,feeId);
        List<Reservation> all=new ArrayList<>(reservations.findAll());all.add(created);reservations.saveAll(all);events.onReservationCreated(created);return created;
    }
    public void cancel(User actor,String reservationId)throws IOException{requireCirculation(actor);List<Reservation> all=new ArrayList<>(reservations.findAll());int i=index(all,reservationId);Reservation old=all.get(i);if(!open(old.status()))throw new IllegalArgumentException("Reservation is already closed.");all.set(i,copy(old,ReservationStatus.CANCELLED,old.readyAt(),old.expiresAt()));reservations.saveAll(all);if(old.status()==ReservationStatus.READY)promote(old.bookId());}
    public List<Reservation> list(){return reservations.findAll().stream().sorted(Comparator.comparingInt((Reservation r) -> statusOrder(r.status())).thenComparing(Reservation::reservedAt)).toList();}
    public List<Reservation> forMember(String memberId){return reservations.findAll().stream().filter(r->r.memberId().equals(memberId)).sorted(Comparator.comparing(Reservation::reservedAt).reversed()).toList();}
    public List<Reservation> forBook(String bookId){return reservations.findAll().stream().filter(r->r.bookId().equals(bookId)).sorted(Comparator.comparing(Reservation::reservedAt)).toList();}
    public int queuePosition(Reservation reservation){int position=1;for(Reservation other:forBook(reservation.bookId())){if(other.reservationId().equals(reservation.reservationId()))break;if(other.status()==ReservationStatus.PENDING)position++;}return position;}
    public int readyCount(String bookId){return (int)reservations.findAll().stream().filter(r->r.bookId().equals(bookId)&&r.status()==ReservationStatus.READY).count();}
    public int lendableCopies(String bookId){return Math.max(0,book(bookId).availableCopies()-readyCount(bookId));}
    public boolean readyFor(String memberId,String bookId){return reservations.findAll().stream().anyMatch(r->r.memberId().equals(memberId)&&r.bookId().equals(bookId)&&r.status()==ReservationStatus.READY);}
    public void markCollected(String memberId,String bookId)throws IOException{List<Reservation> all=new ArrayList<>(reservations.findAll());for(int i=0;i<all.size();i++){Reservation r=all.get(i);if(r.memberId().equals(memberId)&&r.bookId().equals(bookId)&&r.status()==ReservationStatus.READY){Reservation updated=copy(r,ReservationStatus.COLLECTED,r.readyAt(),r.expiresAt());all.set(i,updated);reservations.saveAll(all);events.onReservationCollected(updated);return;}}}
    public int expireReady()throws IOException{int count=0;LocalDateTime now=LocalDateTime.now();List<Reservation> all=new ArrayList<>(reservations.findAll());List<String> booksToPromote=new ArrayList<>();for(int i=0;i<all.size();i++){Reservation r=all.get(i);if(r.status()==ReservationStatus.READY&&r.expiresAt()!=null&&r.expiresAt().isBefore(now)){Reservation updated=copy(r,ReservationStatus.EXPIRED,r.readyAt(),r.expiresAt());all.set(i,updated);events.onReservationExpired(updated);booksToPromote.add(r.bookId());count++;}}if(count>0)reservations.saveAll(all);for(String bookId:booksToPromote)promote(bookId);return count;}
    public Reservation onCopyFreed(String bookId)throws IOException{return promote(bookId);}
    public int cancelOpenForMember(String memberId)throws IOException{List<Reservation> all=new ArrayList<>(reservations.findAll());int count=0;List<String> booksToPromote=new ArrayList<>();for(int i=0;i<all.size();i++){Reservation r=all.get(i);if(r.memberId().equals(memberId)&&open(r.status())){all.set(i,copy(r,ReservationStatus.CANCELLED,r.readyAt(),r.expiresAt()));booksToPromote.add(r.bookId());count++;}}if(count>0)reservations.saveAll(all);for(String bookId:booksToPromote)promote(bookId);return count;}
    private Reservation promote(String bookId)throws IOException{if(lendableCopies(bookId)<=0)return null;List<Reservation> all=new ArrayList<>(reservations.findAll());Reservation next=all.stream().filter(r->r.bookId().equals(bookId)&&r.status()==ReservationStatus.PENDING).filter(r->eligible(r.memberId())).min(Comparator.comparing(Reservation::reservedAt)).orElse(null);if(next==null)return null;LocalDateTime ready=LocalDateTime.now();LocalDateTime expires=ready.toLocalDate().plusDays(settings.getInt("reservation.holdDays")+1).atStartOfDay().minusNanos(1);int i=index(all,next.reservationId());Reservation updated=copy(next,ReservationStatus.READY,ready,expires);all.set(i,updated);reservations.saveAll(all);events.onReservationReady(updated);return updated;}
    private boolean eligible(String memberId){try{return members.findAll().stream().filter(m->m.memberId().equals(memberId)).findFirst().map(m->m.status()==MemberStatus.ACTIVE&&!m.expiredOn(LocalDate.now())).orElse(false);}catch(IOException exception){throw new IllegalStateException("Unable to read member eligibility.",exception);}}
    private List<Reservation> openForMember(String memberId){return reservations.findAll().stream().filter(r->r.memberId().equals(memberId)&&open(r.status())).toList();}
    private static boolean open(ReservationStatus status){return status==ReservationStatus.PENDING||status==ReservationStatus.READY;}
    private static int statusOrder(ReservationStatus status){return switch(status){case READY->0;case PENDING->1;default->2;};}
    private Member findMember(String id) throws IOException{return members.findAll().stream().filter(m->m.memberId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Member does not exist."));}
    private Book findBook(String id) throws IOException{return book(id);}
    private Book book(String id){try{return books.findAll().stream().filter(b->b.bookId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Book does not exist."));}catch(IOException exception){throw new IllegalStateException("Unable to read book availability.",exception);}}
    private void requireCirculation(User actor){if(actor==null||!actor.active()||!permissions.canCirculate(actor))throw new SecurityException("Only active staff or administrators can manage reservations.");}
    private static int index(List<Reservation> all,String id){for(int i=0;i<all.size();i++)if(all.get(i).reservationId().equals(id))return i;throw new IllegalArgumentException("Reservation does not exist.");}
    private static Reservation copy(Reservation r,ReservationStatus status,LocalDateTime ready,LocalDateTime expires){return new Reservation(r.reservationId(),r.bookId(),r.bookTitleSnapshot(),r.memberId(),r.reservedAt(),status,ready,expires,r.feeChargeId());}
}
