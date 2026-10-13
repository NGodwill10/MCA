package src;

import java.util.*;

class GenericBox<T> {
    private T item;

    public void set(T item) {
        this.item = item;
    }

    public T get() {
        return item;
    }
}

public class Main {
    public static <T> void printArray(T[] arr) {
        for (T t : arr) {
            System.out.print(t + " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int choice;

        List<String> students = new ArrayList<>();
        List<String> teachingStaff = new ArrayList<>();
        List<String> nonTeachingStaff = new ArrayList<>();
        List<String> mscAiActivities = new ArrayList<>();
        List<String> mscDsActivities = new ArrayList<>();
        List<String> mcaActivities = new ArrayList<>();

        do {
            System.out.println("\nChoose Academic year");
            System.out.println("1. 2022-2023");
            System.out.println("2. 2023-2024");
            System.out.println("3. 2024-2025");
            System.out.println("4. Generate Report");
            System.out.println("0. Exit");
            System.out.print("\nEnter your choice: ");
            choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {
                case 1:
                case 2:
                case 3:
                    String yrStr = (choice == 1) ? "2022-2023" : (choice == 2) ? "2023-2024" : "2024-2025";
                    System.out.println("\nDCST GBS Report - Academic Year: " + yrStr);
                    System.out.println("1. Student");
                    System.out.println("2. Staff");
                    System.out.println("3. Course");
                    System.out.print("Enter your choice: ");
                    int entityChoice = sc.nextInt();
                    sc.nextLine();

                    if (entityChoice == 1) {
                        System.out.println("1. Add student");
                        System.out.println("2. Display all students");
                        System.out.print("Enter your choice: ");
                        int studentChoice = sc.nextInt();
                        sc.nextLine();

                        if (studentChoice == 1) {
                            System.out.print("Enter student name: ");
                            String name = sc.nextLine();
                            System.out.print("Enter course: ");
                            String course = sc.nextLine();
                            students.add("Name: " + name + ", Course: " + course + " (" + yrStr + ")");
                            System.out.println("Student added successfully.");
                        } else if (studentChoice == 2) {
                            System.out.println("\n--- Students List (" + yrStr + ") ---");
                            if (students.isEmpty()) {
                                System.out.println("No students recorded.");
                            } else {
                                for (String s : students) {
                                    System.out.println(s);
                                }
                            }
                        }
                    } else if (entityChoice == 2) {
                        System.out.println("1. Teaching staff");
                        System.out.println("2. Non-teaching staff");
                        System.out.print("Enter your choice: ");
                        int staffTypeChoice = sc.nextInt();
                        sc.nextLine();

                        System.out.println("1. Add staff");
                        System.out.println("2. Display all staff");
                        System.out.print("Enter your choice: ");
                        int staffActionChoice = sc.nextInt();
                        sc.nextLine();

                        if (staffActionChoice == 1) {
                            System.out.print("Enter staff name: ");
                            String staffName = sc.nextLine();
                            if (staffTypeChoice == 1) {
                                teachingStaff.add(staffName + " (" + yrStr + ")");
                            } else {
                                nonTeachingStaff.add(staffName + " (" + yrStr + ")");
                            }
                            System.out.println("Staff added successfully.");
                        } else if (staffActionChoice == 2) {
                            if (staffTypeChoice == 1) {
                                System.out.println("\n--- Teaching Staff (" + yrStr + ") ---");
                                if (teachingStaff.isEmpty()) {
                                    System.out.println("No teaching staff recorded.");
                                } else {
                                    for (String ts : teachingStaff) {
                                        System.out.println(ts);
                                    }
                                }
                            } else {
                                System.out.println("\n--- Non-Teaching Staff (" + yrStr + ") ---");
                                if (nonTeachingStaff.isEmpty()) {
                                    System.out.println("No non-teaching staff recorded.");
                                } else {
                                    for (String nts : nonTeachingStaff) {
                                        System.out.println(nts);
                                    }
                                }
                            }
                        }
                    } else if (entityChoice == 3) {
                        System.out.println("1. MSc AI");
                        System.out.println("2. MSc DS");
                        System.out.println("3. MCA");
                        System.out.print("Enter your choice: ");
                        int courseChoice = sc.nextInt();
                        sc.nextLine();

                        System.out.println("1. Academic activities");
                        System.out.println("2. Non-Academic activities");
                        System.out.print("Enter your choice: ");
                        sc.nextLine();

                        System.out.println("1. Add new activity");
                        System.out.println("2. Display all activities");
                        System.out.print("Enter your choice: ");
                        int actActionChoice = sc.nextInt();
                        sc.nextLine();

                        if (actActionChoice == 1) {
                            System.out.print("Enter activity name: ");
                            String actName = sc.nextLine();
                            if (courseChoice == 1) {
                                mscAiActivities.add(actName);
                            } else if (courseChoice == 2) {
                                mscDsActivities.add(actName);
                            } else {
                                mcaActivities.add(actName);
                            }
                            System.out.println("Activity added successfully.");
                        } else if (actActionChoice == 2) {
                            List<String> targetList = (courseChoice == 1) ? mscAiActivities
                                    : (courseChoice == 2) ? mscDsActivities : mcaActivities;
                            System.out.println("\n--- Activities ---");
                            if (targetList.isEmpty()) {
                                System.out.println("No activities recorded.");
                            } else {
                                for (String a : targetList) {
                                    System.out.println(a);
                                }
                            }
                        }
                    }
                    break;

                case 4:
                    try {
                        StringBuilder reportBuilder = new StringBuilder();
                        reportBuilder.append("=== NIRF CST REPORT ===\n\n");

                        reportBuilder.append("--- STUDENTS ---\n");
                        if (students.isEmpty()) {
                            reportBuilder.append("No students recorded.\n");
                        } else {
                            for (String s : students) {
                                reportBuilder.append(s).append("\n");
                            }
                        }

                        reportBuilder.append("\n--- TEACHING STAFF ---\n");
                        if (teachingStaff.isEmpty()) {
                            reportBuilder.append("No teaching staff recorded.\n");
                        } else {
                            for (String ts : teachingStaff) {
                                reportBuilder.append(ts).append("\n");
                            }
                        }

                        reportBuilder.append("\n--- NON-TEACHING STAFF ---\n");
                        if (nonTeachingStaff.isEmpty()) {
                            reportBuilder.append("No non-teaching staff recorded.\n");
                        } else {
                            for (String nts : nonTeachingStaff) {
                                reportBuilder.append(nts).append("\n");
                            }
                        }

                        reportBuilder.append("\n--- MSC AI ACTIVITIES ---\n");
                        for (String a : mscAiActivities) {
                            reportBuilder.append(a).append("\n");
                        }

                        reportBuilder.append("\n--- MSC DS ACTIVITIES ---\n");
                        for (String a : mscDsActivities) {
                            reportBuilder.append(a).append("\n");
                        }

                        reportBuilder.append("\n--- MCA ACTIVITIES ---\n");
                        for (String a : mcaActivities) {
                            reportBuilder.append(a).append("\n");
                        }

                        CstDiscipline c1 = new CstDiscipline("MCA");
                        c1.displayDetails();
                        c1.conductAcademic();
                        c1.conductNonAcademic();
                        CstDiscipline.processReport(c1);

                        ReportHandler.saveReport(reportBuilder.toString());
                        System.out.println("Comprehensive NIRF Report generated and saved successfully.");
                    } catch (Exception e) {
                        System.out.println("Error generating report.");
                    }
                    break;

                case 0:
                    System.out.println("Exiting program.");
                    break;

                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        } while (choice != 0);

        sc.close();
    }
}