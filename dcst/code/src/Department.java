package src;

public abstract class Department {
    private String deptName;
    protected String schoolName;
    public int memberId;
    private static int counter = 0;

    public enum Year {
        Y2022_2023, Y2023_2024, Y2024_2025
    }

    public Department(String deptName, String schoolName) {
        this.deptName = deptName;
        this.schoolName = schoolName;
        counter++;
        this.memberId = counter;
    }

    public static int getCounter() {
        return counter;
    }

    public String getDeptName() {
        return deptName;
    }

    public abstract void displayDetails();

    public void showInfo(String detail) {
        System.out.println("Info: " + detail);
    }

    public void showInfo(String detail, int code) {
        System.out.println("Info: " + detail + " Code: " + code);
    }
}