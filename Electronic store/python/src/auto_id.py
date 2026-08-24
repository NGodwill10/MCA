from pathlib import Path
from src.file_manager import read_all_lines

def get_next_id(file_path: Path) -> int:
    lines = read_all_lines(file_path)
    if not lines:
        return 1
    last_line = lines[-1]
    try:
        last_id = int(last_line.split(",")[0])
        return last_id + 1
    except (IndexError, ValueError):
        return 1