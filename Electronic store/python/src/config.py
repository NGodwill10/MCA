from pathlib import Path

# Change this path to update location everywhere
DATA_DIR = Path(__file__).resolve().parent.parent / "data"

# Individual file paths
CUSTOMERS_FILE = DATA_DIR / "customers.txt"
PRODUCTS_FILE = DATA_DIR / "products.txt"
STAFF_FILE = DATA_DIR / "staff.txt"
STAKEHOLDERS_FILE = DATA_DIR / "stakeholders.txt"
STOCK_FILE = DATA_DIR / "stock.txt"