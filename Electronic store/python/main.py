from pathlib import Path
from src.config import CUSTOMERS_FILE, PRODUCTS_FILE, STAFF_FILE, STAKEHOLDERS_FILE
from src import file_manager, auto_id, store_services
from src.models import Customer, Staff, Product, Stakeholder

def remove_entry(file_path: Path) -> None:
    target_id = int(input("Enter ID to remove: "))
    lines = file_manager.read_all_lines(file_path)
    updated_lines = []
    removed = False

    for line in lines:
        entry_id = int(line.split(",")[0])
        if entry_id == target_id:
            removed = True
        else:
            updated_lines.append(line)

    if removed:
        file_manager.write_all_lines(file_path, updated_lines)
        print("Record removed successfully.")
    else:
        print("Record ID not found.")

def list_entries(file_path: Path) -> None:
    lines = file_manager.read_all_lines(file_path)
    print("\n--- Records ---")
    if not lines:
        print("No records found.")
    else:
        for line in lines:
            print(line)

def customer_menu() -> None:
    print("\n--- Customer Management ---")
    print("1. Add Customer\n2. Remove Customer\n3. List Customers\n4. Generate Bill\n5. Request Repair")
    choice = int(input("Select: "))

    if choice == 1:
        c_id = auto_id.get_next_id(CUSTOMERS_FILE)
        name = input("Enter Name: ")
        addr = input("Enter Address: ")
        contact = input("Enter Contact: ")
        file_manager.append_line(CUSTOMERS_FILE, Customer(c_id, name, addr, contact).to_csv())
        print(f"Customer added with ID: {c_id}")
    elif choice == 2:
        remove_entry(CUSTOMERS_FILE)
    elif choice == 3:
        list_entries(CUSTOMERS_FILE)
    elif choice == 4:
        p_ids = []
        while True:
            pid = int(input("Enter Product ID to add to bill (0 to complete): "))
            if pid == 0:
                break
            p_ids.append(pid)
        store_services.process_billing(p_ids)
    elif choice == 5:
        store_services.request_repair()

def staff_menu() -> None:
    print("\n--- Staff Management ---")
    print("1. Add Staff\n2. Remove Staff\n3. List Staff")
    choice = int(input("Select: "))

    if choice == 1:
        s_id = auto_id.get_next_id(STAFF_FILE)
        name = input("Enter Name: ")
        addr = input("Enter Address: ")
        salary = float(input("Enter Salary: "))
        file_manager.append_line(STAFF_FILE, Staff(s_id, name, addr, salary).to_csv())
        print(f"Staff added with ID: {s_id}")
    elif choice == 2:
        remove_entry(STAFF_FILE)
    elif choice == 3:
        list_entries(STAFF_FILE)

def product_menu() -> None:
    print("\n--- Product Management ---")
    print("1. Add Product\n2. Remove Product\n3. List Products")
    choice = int(input("Select: "))

    if choice == 1:
        p_id = auto_id.get_next_id(PRODUCTS_FILE)
        name = input("Enter Name: ")
        cat = input("Enter Category: ")
        price = float(input("Enter Price: "))
        file_manager.append_line(PRODUCTS_FILE, Product(p_id, name, cat, price).to_csv())
        print(f"Product added with ID: {p_id}")
    elif choice == 2:
        remove_entry(PRODUCTS_FILE)
    elif choice == 3:
        list_entries(PRODUCTS_FILE)

def stakeholder_menu() -> None:
    print("\n--- Stakeholder Management ---")
    print("1. Add Stakeholder\n2. Remove Stakeholder\n3. List Stakeholders")
    choice = int(input("Select: "))

    if choice == 1:
        st_id = auto_id.get_next_id(STAKEHOLDERS_FILE)
        name = input("Enter Name: ")
        email = input("Enter Email: ")
        st_type = input("Type (Owner/Investor): ")
        stake = float(input("Stake %: "))
        inv = float(input("Investment: "))
        file_manager.append_line(STAKEHOLDERS_FILE, Stakeholder(st_id, name, email, st_type, stake, inv).to_csv())
        print(f"Stakeholder added with ID: {st_id}")
    elif choice == 2:
        remove_entry(STAKEHOLDERS_FILE)
    elif choice == 3:
        list_entries(STAKEHOLDERS_FILE)

def main() -> None:
    while True:
        print("\n=====================================")
        print(" ELECTRONIC STORE MANAGEMENT SYSTEM ")
        print("=====================================")
        print("1. Customer Management")
        print("2. Staff Management")
        print("3. Product Management")
        print("4. Stakeholder Management")
        print("0. Exit")
        choice = int(input("Enter Choice: "))

        if choice == 1:
            customer_menu()
        elif choice == 2:
            staff_menu()
        elif choice == 3:
            product_menu()
        elif choice == 4:
            stakeholder_menu()
        elif choice == 0:
            print("Exiting system. Goodbye!")
            break
        else:
            print("Invalid Choice!")

if __name__ == "__main__":
    main()