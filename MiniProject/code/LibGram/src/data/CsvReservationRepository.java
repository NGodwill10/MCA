package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Reservation;
import model.ReservationStatus;

public final class CsvReservationRepository implements ReservationRepository {
    private static final List<String> HEADER = List.of("reservationId", "bookId", "bookTitleSnapshot", "memberId",
            "reservedAt", "status", "readyAt", "expiresAt", "feeChargeId");
    private final Path file;
    private List<Reservation> reservations;
    public CsvReservationRepository(Path file) throws IOException { this.file=file; reservations=load(); if(!Files.exists(file)) write(reservations); }
    public List<Reservation> findAll() { return List.copyOf(reservations); }
    public void saveAll(List<Reservation> values) throws IOException { List<Reservation> copy=List.copyOf(values); write(copy); reservations=copy; }
    private List<Reservation> load() throws IOException { List<List<String>> rows=CsvUtil.read(file); if(rows.isEmpty())return List.of(); List<String> header=rows.getFirst(); List<Reservation> result=new ArrayList<>(); for(int i=1;i<rows.size();i++)try{List<String> row=rows.get(i);result.add(new Reservation(value(row,header,"reservationId"),value(row,header,"bookId"),value(row,header,"bookTitleSnapshot"),value(row,header,"memberId"),LocalDateTime.parse(value(row,header,"reservedAt")),ReservationStatus.valueOf(value(row,header,"status").toUpperCase(Locale.ROOT)),date(value(row,header,"readyAt")),date(value(row,header,"expiresAt")),value(row,header,"feeChargeId")));}catch(RuntimeException ex){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Skipping malformed reservation row",ex);} return List.copyOf(result); }
    private void write(List<Reservation> values)throws IOException{CsvUtil.writeAtomically(file,HEADER,values.stream().map(r->List.of(r.reservationId(),r.bookId(),r.bookTitleSnapshot(),r.memberId(),r.reservedAt().toString(),r.status().name(),text(r.readyAt()),text(r.expiresAt()),r.feeChargeId())).toList());}
    private static String value(List<String> row,List<String> header,String key){int i=header.indexOf(key);return i>=0&&i<row.size()?row.get(i):"";}
    private static LocalDateTime date(String value){return value.isBlank()?null:LocalDateTime.parse(value);}
    private static String text(LocalDateTime value){return value==null?"":value.toString();}
}
