from dataclasses import dataclass

@dataclass
class Stakeholder:
    id: int
    name: str
    email: str
    type: str  # Owner or Investor
    stake: float
    investment: float

    def to_csv(self) -> str:
        return f"{self.id},{self.name},{self.email},{self.type},{self.stake},{self.investment}"

@dataclass
class Customer:
    id: int
    name: str
    address: str
    contact: str

    def to_csv(self) -> str:
        return f"{self.id},{self.name},{self.address},{self.contact}"

@dataclass
class Staff:
    id: int
    name: str
    address: str
    salary: float

    def to_csv(self) -> str:
        return f"{self.id},{self.name},{self.address},{self.salary}"

@dataclass
class Product:
    id: int
    name: str
    category: str
    price: float

    def to_csv(self) -> str:
        return f"{self.id},{self.name},{self.category},{self.price}"

@dataclass
class StockItem:
    product_id: int
    store_qty: int
    warehouse_qty: int

    def to_csv(self) -> str:
        return f"{self.product_id},{self.store_qty},{self.warehouse_qty}"