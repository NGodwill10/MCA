package src;

public class CstDiscipline extends Department implements AcademicActivity, NonAcademicActivity, Report, Cloneable {
    private String programName;

    public CstDiscipline(String programName) {
        super("DCST", "Goa Business School");
        this.programName = programName;
    }

    @Override
    public void displayDetails() {
        System.out.println("Program: " + programName + " at " + schoolName);
    }

    @Override
    public void conductAcademic() {
        System.out.println("Academic Activities: ISA, Midpoint, SEA, Study-tour, Placements");
    }

    @Override
    public void conductNonAcademic() {
        System.out.println("Non-Academic Activities: InfoFest, Plateaunia, Play-on");
    }

    @Override
    public void generateReport() {
        System.out.println("Generating NIRF Report for CST Discipline - " + programName);
    }

    public static void processReport(Report r) {
        r.generateReport();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}