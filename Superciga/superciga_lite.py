from __future__ import annotations

import csv
import json
import re
import sqlite3
import tkinter as tk
import webbrowser
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

APP_VERSION = "2.1-github"
APP_DIR = Path(__file__).resolve().parent
DB_PATH = APP_DIR / "data" / "superciga.db"
I9_URL = "https://www.i9orcamentos.com.br/sistema/pesquisar"
CIGA_URL = "https://obras-ng.ciga.sc.gov.br/#!/"


@dataclass
class Item:
    id: int | None
    tabela: str
    codigo: str
    descricao: str
    unidade: str
    valor: float
    referencia: str = ""
    uf: str = ""


class DB:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(path)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute(
            """
            create table if not exists itens (
                id integer primary key autoincrement,
                tabela text not null default '',
                codigo text not null default '',
                descricao text not null default '',
                unidade text not null default '',
                valor real not null default 0,
                referencia text not null default '',
                uf text not null default '',
                data_importacao text not null default '',
                unique(tabela, codigo, descricao, unidade, referencia, uf)
            )
            """
        )
        self.conn.execute(
            """
            create table if not exists orcamento (
                id integer primary key autoincrement,
                item_id integer,
                grupo text not null default 'GERAL',
                qtd real not null default 1,
                tabela text not null default '',
                codigo text not null default '',
                descricao text not null default '',
                unidade text not null default '',
                valor real not null default 0
            )
            """
        )
        self.conn.commit()

    def upsert(self, items: list[Item]) -> int:
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        for item in items:
            self.conn.execute(
                """
                insert into itens(tabela,codigo,descricao,unidade,valor,referencia,uf,data_importacao)
                values(?,?,?,?,?,?,?,?)
                on conflict(tabela,codigo,descricao,unidade,referencia,uf) do update set
                  valor=excluded.valor, data_importacao=excluded.data_importacao
                """,
                (item.tabela, item.codigo, item.descricao, item.unidade, item.valor, item.referencia, item.uf, now),
            )
        self.conn.commit()
        return len(items)

    def search(self, text: str = "", tabela: str = "") -> list[Item]:
        where: list[str] = []
        params: list[object] = []
        if tabela.strip():
            where.append("lower(tabela) like ?")
            params.append(f"%{tabela.strip().lower()}%")
        for term in text.split():
            where.append("(lower(codigo) like ? or lower(descricao) like ?)")
            params += [f"%{term.lower()}%", f"%{term.lower()}%"]
        sql = "select * from itens"
        if where:
            sql += " where " + " and ".join(where)
        sql += " order by tabela, codigo limit 500"
        return [row_to_item(r) for r in self.conn.execute(sql, params).fetchall()]

    def get(self, item_id: int) -> Item | None:
        row = self.conn.execute("select * from itens where id=?", (item_id,)).fetchone()
        return row_to_item(row) if row else None

    def add_budget(self, item: Item, grupo: str, qtd: float) -> None:
        self.conn.execute(
            "insert into orcamento(item_id,grupo,qtd,tabela,codigo,descricao,unidade,valor) values(?,?,?,?,?,?,?,?)",
            (item.id, grupo, qtd, item.tabela, item.codigo, item.descricao, item.unidade, item.valor),
        )
        self.conn.commit()

    def list_budget(self):
        return self.conn.execute("select *, qtd*valor total from orcamento order by id").fetchall()

    def delete_budget(self, line_id: int) -> None:
        self.conn.execute("delete from orcamento where id=?", (line_id,))
        self.conn.commit()

    def clear_budget(self) -> None:
        self.conn.execute("delete from orcamento")
        self.conn.commit()


def row_to_item(row: sqlite3.Row) -> Item:
    return Item(
        id=int(row["id"]),
        tabela=row["tabela"],
        codigo=row["codigo"],
        descricao=row["descricao"],
        unidade=row["unidade"],
        valor=float(row["valor"] or 0),
        referencia=row["referencia"],
        uf=row["uf"],
    )


def money(value: float) -> str:
    s = f"R$ {value:,.2f}"
    return s.replace(",", "X").replace(".", ",").replace("X", ".")


def parse_number(text: str) -> float:
    text = str(text or "0").replace("R$", "").replace(" ", "")
    if "," in text and "." in text:
        text = text.replace(".", "").replace(",", ".")
    elif "," in text:
        text = text.replace(",", ".")
    text = re.sub(r"[^0-9.\-]", "", text)
    return float(text or 0)


def parse_clipboard(text: str, default_table: str = "") -> list[Item]:
    lines = [l for l in text.splitlines() if l.strip()]
    if not lines:
        return []
    delim = "\t" if "\t" in lines[0] else ";" if ";" in lines[0] else ","
    rows = list(csv.reader(lines, delimiter=delim))
    header = [h.strip().lower() for h in rows[0]]
    has_header = any("codigo" in h or "código" in h for h in header) and any("descr" in h for h in header)
    data_rows = rows[1:] if has_header else rows

    def idx(*names: str) -> int | None:
        for name in names:
            for i, h in enumerate(header):
                if name in h:
                    return i
        return None

    items: list[Item] = []
    for cells in data_rows:
        cells = [c.strip() for c in cells]
        if not cells:
            continue
        if has_header:
            def val(i: int | None) -> str:
                return cells[i] if i is not None and i < len(cells) else ""
            tabela = val(idx("tabela", "base")) or default_table
            codigo = val(idx("codigo", "código", "cod"))
            descricao = val(idx("descr"))
            unidade = val(idx("unidade", " un", "und")) or "UN"
            referencia = val(idx("refer", "liber"))
            valor_txt = val(idx("valor", "preço", "preco", "custo"))
        else:
            while cells and cells[0].upper() in {"C", "I", "INS./COMP", "INSUMO", "COMPOSICAO", "COMPOSIÇÃO"}:
                cells.pop(0)
            if len(cells) >= 4 and re.match(r"^[A-Z]{2,}([-/][A-Z]{2})?$", cells[0].upper()):
                tabela, codigo, descricao, unidade = cells[:4]
                tail = cells[4:]
            elif len(cells) >= 3:
                tabela = default_table
                codigo, descricao, unidade = cells[:3]
                tail = cells[3:]
            else:
                continue
            referencia = ""
            valor_txt = ""
            for c in tail:
                if not referencia:
                    m = re.search(r"\b(0?[1-9]|1[0-2])/[12]\d{3}\b", c)
                    if m:
                        referencia = m.group(0)
                if not valor_txt and re.search(r"\d+[,.]\d{2}", c):
                    valor_txt = c
        if not codigo or not descricao or unidade.upper() in {"MATERIAL", "SERVIÇO", "SERVICO"}:
            continue
        items.append(Item(None, tabela.strip(), codigo.strip(), descricao.strip(), unidade.strip().upper(), parse_number(valor_txt), referencia.strip()))
    return items


def build_script(item: Item) -> str:
    payload = asdict(item)
    payload["observacao"] = f"Fonte externa: {item.tabela} {item.referencia}. Código origem: {item.codigo}. Criado pelo Superciga."
    data = json.dumps(payload, ensure_ascii=False)
    return f"""(async () => {{
  const item = {data};
  alert('Superciga: confira e cadastre como cotação externa no CIGA.\\n' + item.tabela + ' ' + item.codigo + ' - ' + item.descricao + '\\nValor: ' + item.valor);
  console.log('SUPERCIGA_ITEM', item);
}})();"""


class App(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(f"Superciga {APP_VERSION}")
        self.geometry("1180x720")
        self.minsize(980, 620)
        self.db = DB(DB_PATH)
        self.selected: Item | None = None
        self._style()
        self._ui()
        self.search()
        self.refresh_budget()

    def _style(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        self.configure(bg="#f4f6f8")
        style.configure("TFrame", background="#f4f6f8")
        style.configure("TLabel", background="#f4f6f8", font=("Segoe UI", 9))
        style.configure("TButton", padding=(8, 5), font=("Segoe UI", 9))
        style.configure("Primary.TButton", padding=(8, 5), font=("Segoe UI", 9, "bold"))
        style.configure("Treeview", rowheight=26, font=("Segoe UI", 9))
        style.configure("Treeview.Heading", font=("Segoe UI", 9, "bold"))

    def _ui(self) -> None:
        top = ttk.Frame(self, padding=12)
        top.pack(fill="x")
        ttk.Label(top, text="Superciga", font=("Segoe UI", 15, "bold")).pack(side="left")
        ttk.Label(top, text="  Pesquise, cole itens do i9 e gere texto/script para cotação externa no CIGA.").pack(side="left")

        bar = ttk.Frame(self, padding=(12, 0, 12, 8))
        bar.pack(fill="x")
        ttk.Button(bar, text="Abrir i9", command=lambda: webbrowser.open(I9_URL)).pack(side="left", padx=(0, 6))
        ttk.Button(bar, text="Abrir CIGA", command=lambda: webbrowser.open(CIGA_URL)).pack(side="left", padx=(0, 12))
        ttk.Button(bar, text="Colar tabela/linha", style="Primary.TButton", command=self.import_clipboard).pack(side="left", padx=(0, 6))
        ttk.Button(bar, text="Importar CSV/TXT", command=self.import_file).pack(side="left", padx=(0, 6))
        ttk.Button(bar, text="Item manual", command=self.manual_item).pack(side="left")

        filt = ttk.Frame(self, padding=(12, 0, 12, 8))
        filt.pack(fill="x")
        ttk.Label(filt, text="Tabela").pack(side="left")
        self.tabela = tk.StringVar()
        ttk.Entry(filt, textvariable=self.tabela, width=18).pack(side="left", padx=(5, 12))
        ttk.Label(filt, text="Busca").pack(side="left")
        self.busca = tk.StringVar()
        ent = ttk.Entry(filt, textvariable=self.busca, width=55)
        ent.pack(side="left", padx=(5, 8), fill="x", expand=True)
        ent.bind("<Return>", lambda _e: self.search())
        ttk.Button(filt, text="Pesquisar", style="Primary.TButton", command=self.search).pack(side="left", padx=(0, 6))
        ttk.Button(filt, text="Limpar", command=self.clear).pack(side="left")

        panes = ttk.PanedWindow(self, orient="horizontal")
        panes.pack(fill="both", expand=True, padx=12, pady=(0, 8))
        left = ttk.Frame(panes)
        right = ttk.Frame(panes)
        panes.add(left, weight=3)
        panes.add(right, weight=2)

        cols = ("id", "tabela", "codigo", "descricao", "un", "valor", "ref")
        self.tree = ttk.Treeview(left, columns=cols, show="headings")
        widths = {"id": 45, "tabela": 110, "codigo": 105, "descricao": 520, "un": 55, "valor": 90, "ref": 90}
        labels = {"id": "ID", "tabela": "TABELA", "codigo": "CÓDIGO", "descricao": "DESCRIÇÃO", "un": "UN", "valor": "VALOR", "ref": "REF."}
        for c in cols:
            self.tree.heading(c, text=labels[c])
            self.tree.column(c, width=widths[c], stretch=(c == "descricao"))
        self.tree.pack(side="left", fill="both", expand=True)
        sy = ttk.Scrollbar(left, command=self.tree.yview)
        sy.pack(side="right", fill="y")
        self.tree.configure(yscrollcommand=sy.set)
        self.tree.bind("<<TreeviewSelect>>", self.select)
        self.tree.bind("<Double-1>", lambda _e: self.generate_script())

        tabs = ttk.Notebook(right)
        tabs.pack(fill="both", expand=True)
        tab_item = ttk.Frame(tabs, padding=8)
        tab_orc = ttk.Frame(tabs, padding=8)
        tab_script = ttk.Frame(tabs, padding=8)
        tabs.add(tab_item, text="Item")
        tabs.add(tab_orc, text="Orçamento local")
        tabs.add(tab_script, text="CIGA / Script")

        self.details = tk.Text(tab_item, height=16, wrap="word", font=("Segoe UI", 10))
        self.details.pack(fill="both", expand=True)
        b = ttk.Frame(tab_item)
        b.pack(fill="x", pady=(8, 0))
        ttk.Button(b, text="Copiar item", command=self.copy_item).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ttk.Button(b, text="Copiar código", command=lambda: self.copy_field("codigo")).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ttk.Button(b, text="Copiar descrição", command=lambda: self.copy_field("descricao")).pack(side="left", fill="x", expand=True)

        line = ttk.Frame(tab_orc)
        line.pack(fill="x")
        ttk.Label(line, text="Grupo").pack(side="left")
        self.grupo = tk.StringVar(value="GERAL")
        ttk.Entry(line, textvariable=self.grupo, width=20).pack(side="left", padx=(5, 10))
        ttk.Label(line, text="Qtd.").pack(side="left")
        self.qtd = tk.StringVar(value="1")
        ttk.Entry(line, textvariable=self.qtd, width=8).pack(side="left", padx=(5, 10))
        ttk.Button(line, text="Adicionar", style="Primary.TButton", command=self.add_budget).pack(side="left", fill="x", expand=True)
        self.budget = ttk.Treeview(tab_orc, columns=("id", "grupo", "codigo", "desc", "qtd", "un", "unit", "total"), show="headings", height=10)
        for c, w in {"id": 35, "grupo": 80, "codigo": 85, "desc": 220, "qtd": 60, "un": 45, "unit": 80, "total": 85}.items():
            self.budget.heading(c, text=c.upper())
            self.budget.column(c, width=w, stretch=(c == "desc"))
        self.budget.pack(fill="both", expand=True, pady=(8, 0))
        ob = ttk.Frame(tab_orc)
        ob.pack(fill="x", pady=(8, 0))
        ttk.Button(ob, text="Exportar CSV", command=self.export_budget).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ttk.Button(ob, text="Remover", command=self.remove_budget).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ttk.Button(ob, text="Limpar", command=self.clear_budget).pack(side="left", fill="x", expand=True)
        self.total = tk.StringVar(value="Total: R$ 0,00")
        ttk.Label(tab_orc, textvariable=self.total, font=("Segoe UI", 10, "bold")).pack(anchor="e", pady=(8, 0))

        ttk.Button(tab_script, text="Gerar script", style="Primary.TButton", command=self.generate_script).pack(fill="x", pady=(0, 6))
        ttk.Button(tab_script, text="Copiar script", command=self.copy_script).pack(fill="x", pady=(0, 8))
        self.script = tk.Text(tab_script, wrap="none", font=("Consolas", 9))
        self.script.pack(fill="both", expand=True)

        self.status = tk.StringVar(value="Pronto.")
        ttk.Label(self, textvariable=self.status, anchor="w").pack(fill="x", padx=12, pady=(0, 8))

    def set_clip(self, text: str, msg: str) -> None:
        self.clipboard_clear()
        self.clipboard_append(text)
        self.update_idletasks()
        self.status.set(msg)

    def import_clipboard(self) -> None:
        try:
            text = self.clipboard_get()
        except tk.TclError:
            messagebox.showwarning("Clipboard vazio", "Copie a linha/tabela do i9 ou Excel antes.")
            return
        items = parse_clipboard(text, self.tabela.get())
        if not items:
            messagebox.showwarning("Não reconhecido", "Não consegui reconhecer código, descrição, unidade e valor.")
            return
        self.db.upsert(items)
        self.search()
        self.status.set(f"{len(items)} item(ns) importado(s).")

    def import_file(self) -> None:
        path = filedialog.askopenfilename(filetypes=[("Texto/CSV", "*.csv *.txt"), ("Todos", "*.*")])
        if not path:
            return
        text = Path(path).read_text(encoding="utf-8-sig", errors="replace")
        items = parse_clipboard(text, self.tabela.get())
        self.db.upsert(items)
        self.search()
        self.status.set(f"{len(items)} item(ns) importado(s) do arquivo.")

    def manual_item(self) -> None:
        win = tk.Toplevel(self)
        win.title("Item manual")
        vars = {k: tk.StringVar() for k in ["tabela", "codigo", "descricao", "unidade", "valor", "referencia", "uf"]}
        vars["tabela"].set(self.tabela.get())
        vars["unidade"].set("UN")
        for r, k in enumerate(vars):
            ttk.Label(win, text=k.upper()).grid(row=r, column=0, sticky="w", padx=8, pady=4)
            ttk.Entry(win, textvariable=vars[k], width=65 if k == "descricao" else 30).grid(row=r, column=1, padx=8, pady=4)
        def save() -> None:
            item = Item(None, vars["tabela"].get(), vars["codigo"].get(), vars["descricao"].get(), vars["unidade"].get() or "UN", parse_number(vars["valor"].get()), vars["referencia"].get(), vars["uf"].get())
            if not item.codigo or not item.descricao:
                messagebox.showerror("Faltando dados", "Informe código e descrição.")
                return
            self.db.upsert([item])
            win.destroy()
            self.search()
        ttk.Button(win, text="Salvar", style="Primary.TButton", command=save).grid(row=len(vars), column=1, sticky="e", padx=8, pady=8)

    def search(self) -> None:
        for i in self.tree.get_children():
            self.tree.delete(i)
        items = self.db.search(self.busca.get(), self.tabela.get())
        for it in items:
            self.tree.insert("", "end", iid=str(it.id), values=(it.id, it.tabela, it.codigo, it.descricao, it.unidade, money(it.valor), it.referencia))
        self.status.set(f"{len(items)} item(ns) encontrado(s).")

    def clear(self) -> None:
        self.busca.set("")
        self.tabela.set("")
        self.search()

    def select(self, _event=None) -> None:
        sel = self.tree.selection()
        if not sel:
            return
        self.selected = self.db.get(int(sel[0]))
        self.render_details()

    def render_details(self) -> None:
        self.details.delete("1.0", "end")
        if self.selected:
            it = self.selected
            self.details.insert("1.0", f"Tabela: {it.tabela}\nCódigo: {it.codigo}\nDescrição: {it.descricao}\nUnidade: {it.unidade}\nValor: {money(it.valor)}\nReferência: {it.referencia}\nUF: {it.uf}\n")

    def ensure_selected(self) -> bool:
        if not self.selected:
            messagebox.showwarning("Selecione um item", "Selecione um item na lista primeiro.")
            return False
        return True

    def copy_item(self) -> None:
        if not self.ensure_selected():
            return
        it = self.selected
        self.set_clip(f"{it.tabela}\t{it.codigo}\t{it.descricao}\t{it.unidade}\t{money(it.valor)}", "Item copiado.")

    def copy_field(self, name: str) -> None:
        if self.ensure_selected():
            self.set_clip(str(getattr(self.selected, name)), f"{name} copiado.")

    def generate_script(self) -> None:
        if not self.ensure_selected():
            return
        self.script.delete("1.0", "end")
        self.script.insert("1.0", build_script(self.selected))
        self.status.set("Script gerado. Clique em copiar script.")

    def copy_script(self) -> None:
        text = self.script.get("1.0", "end").strip()
        if not text:
            self.generate_script()
            text = self.script.get("1.0", "end").strip()
        if text:
            self.set_clip(text, "Script copiado.")

    def add_budget(self) -> None:
        if not self.ensure_selected():
            return
        qtd = parse_number(self.qtd.get() or "1")
        self.db.add_budget(self.selected, self.grupo.get() or "GERAL", qtd)
        self.refresh_budget()

    def refresh_budget(self) -> None:
        for i in self.budget.get_children():
            self.budget.delete(i)
        total = 0.0
        for r in self.db.list_budget():
            total += float(r["total"] or 0)
            self.budget.insert("", "end", iid=str(r["id"]), values=(r["id"], r["grupo"], r["codigo"], r["descricao"][:80], r["qtd"], r["unidade"], money(r["valor"]), money(r["total"])))
        self.total.set(f"Total: {money(total)}")

    def remove_budget(self) -> None:
        sel = self.budget.selection()
        if sel:
            self.db.delete_budget(int(sel[0]))
            self.refresh_budget()

    def clear_budget(self) -> None:
        if messagebox.askyesno("Limpar", "Limpar orçamento local?"):
            self.db.clear_budget()
            self.refresh_budget()

    def export_budget(self) -> None:
        path = filedialog.asksaveasfilename(defaultextension=".csv", initialfile="orcamento_superciga.csv")
        if not path:
            return
        rows = self.db.list_budget()
        with open(path, "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f, delimiter=";")
            w.writerow(["grupo", "tabela", "codigo", "descricao", "unidade", "quantidade", "valor_unitario", "total"])
            for r in rows:
                w.writerow([r["grupo"], r["tabela"], r["codigo"], r["descricao"], r["unidade"], r["qtd"], r["valor"], r["total"]])
        self.status.set(f"CSV exportado: {path}")


def main() -> None:
    App().mainloop()


if __name__ == "__main__":
    main()
