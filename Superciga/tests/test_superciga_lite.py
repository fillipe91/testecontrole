from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from superciga_lite import Item, build_script, parse_clipboard, parse_number


def test_parse_number_brazilian_money() -> None:
    assert parse_number("R$ 1.234,56") == 1234.56
    assert parse_number("28,41") == 28.41


def test_parse_clipboard_tabular_line() -> None:
    text = "SINAPI/SC\t91926\tCABO DE COBRE FLEXIVEL ISOLADO\tM\tR$ 8,50\t04/2026"
    items = parse_clipboard(text)
    assert len(items) == 1
    item = items[0]
    assert item.tabela == "SINAPI/SC"
    assert item.codigo == "91926"
    assert item.unidade == "M"
    assert item.valor == 8.50
    assert item.referencia == "04/2026"


def test_build_script_contains_item_data() -> None:
    item = Item(
        id=1,
        tabela="ORSE",
        codigo="04622",
        descricao="Cabo multiplexado de aluminio",
        unidade="m",
        valor=28.41,
        referencia="05/2026",
        uf="SE",
    )
    script = build_script(item)
    assert "SUPERCIGA_ITEM" in script
    assert "04622" in script
    assert "Cabo multiplexado" in script


if __name__ == "__main__":
    test_parse_number_brazilian_money()
    test_parse_clipboard_tabular_line()
    test_build_script_contains_item_data()
    print("Todos os testes básicos do Superciga passaram.")
