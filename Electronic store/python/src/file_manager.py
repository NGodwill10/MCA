from pathlib import Path
from typing import List

def ensure_file_exists(file_path: Path) -> None:
    file_path.parent.mkdir(parents=True, exist_ok=True)
    if not file_path.exists():
        file_path.touch()

def read_all_lines(file_path: Path) -> List[str]:
    ensure_file_exists(file_path)
    with open(file_path, "r", encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip()]

def append_line(file_path: Path, line: str) -> None:
    ensure_file_exists(file_path)
    with open(file_path, "a", encoding="utf-8") as f:
        f.write(f"{line}\n")

def write_all_lines(file_path: Path, lines: List[str]) -> None:
    ensure_file_exists(file_path)
    with open(file_path, "w", encoding="utf-8") as f:
        for line in lines:
            f.write(f"{line}\n")