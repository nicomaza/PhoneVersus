import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import Papa from 'papaparse';
import { map, Observable } from 'rxjs';
// lista-precios.service.ts (agregá estos campos)
export interface ProductoLista {
  marca: string;
  categoriaRaw: string;
  modelo: string;
  efectivo?: number;
  transferencia?: number;
  tarjeta?: number;
  colores?: string[];

  searchText: string;
  searchCompact: string;

  modelText: string;       // SOLO modelo normalizado
  modelCompact: string;    // SOLO modelo normalizado sin espacios
}


@Injectable({
  providedIn: 'root'
})
export class ListaPreciosService {
  private readonly SHEET_ID = '1MtaCGCL0fGMg25RdshtRKcakiy-ggLCjSeuhiKy4Zdk';
  private readonly GID = '0';

  private readonly CSV_URL =
    `https://docs.google.com/spreadsheets/d/${this.SHEET_ID}/export?format=csv&gid=${this.GID}`;

  constructor(private http: HttpClient) {}

  getProductos(): Observable<ProductoLista[]> {
    return this.http.get(this.CSV_URL, { responseType: 'text' }).pipe(
      map(csv => Papa.parse<string[]>(csv, { skipEmptyLines: false }).data as string[][]),
      map(rows => this.parseRows(rows)),
    );
  }

  private parseRows(rows: string[][]): ProductoLista[] {
    let categoriaRaw = '';
    let marcaActual = '';

    const productos: ProductoLista[] = [];

    const isSeparator = (v: string) => /^-+$/.test(v.trim());
    const isCategory = (v: string) => v.trim().startsWith('➡️');
    const isModel = (v: string) => /^\*[^*]+\*/.test(v.trim());
    const isColors = (v: string) => /^_.*_$/.test(v.trim());  // _Black,Blue_

    const cleanModel = (v: string) => {
  const s = v.trim();
  const m = s.match(/^\*(.*?)\*(.*)$/); // 1=entre *, 2=lo de después
  if (!m) return s.replace(/^\*|\*$/g, '').trim();

  const core = (m[1] ?? '').trim();
  const rest = (m[2] ?? '').trim(); // ej "(8GB+8GB)"
  return rest ? `${core} ${rest}` : core;
};
    const cleanColors = (v: string) =>
      v.trim()
        .replace(/^_+|_+$/g, '')
        .split(',')
        .map(x => x.trim())
        .filter(Boolean);

    const parseMoney = (v: string | undefined) => {
      if (!v) return undefined;
      const n = v.replace(/[^\d]/g, '');
      return n ? Number(n) : undefined;
    };

    const extractBrand = (categoryLine: string) => {
      // "➡️ Samsung📱(son todos importados...)" -> "Samsung"
      let s = categoryLine.replace(/^➡️\s*/g, '').trim();
      s = s.split('(')[0].trim(); // antes de paréntesis
      // quitar emojis/símbolos raros, dejar letras/números/espacios
      s = s.replace(/[^A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ\s-]/g, '').trim();
      // en caso de que quede vacío por un formato raro
      return s || 'Sin marca';
    };

    const normalize = (txt: string) => {
      // lower + sin acentos + solo alfanumérico/espacios + colapsar
      const noAccents = txt
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '');
      return noAccents
        .replace(/[^a-z0-9\s]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    };

    for (let i = 0; i < rows.length; i++) {
      const [c1, c2, c3, c4] = rows[i].map(x => (x ?? '').trim());

      if (!c1 && !c2 && !c3 && !c4) continue;
      if (c1 && isSeparator(c1)) continue;

      if (c1 && isCategory(c1)) {
        categoriaRaw = c1;
        marcaActual = extractBrand(c1);
        continue;
      }

      // líneas sueltas (títulos) que no son modelos ni colores
      if (c1 && !isModel(c1) && !isColors(c1) && !isCategory(c1)) {
        continue;
      }

      if (c1 && isModel(c1)) {
        const modelo = cleanModel(c1);

const baseSearch = `${marcaActual} ${modelo}`;

const searchText = normalize(baseSearch);
const searchCompact = searchText.replace(/\s/g, '');

const modelText = normalize(modelo);
const modelCompact = modelText.replace(/\s/g, '');

const producto: ProductoLista = {
  marca: marcaActual || 'Sin marca',
  categoriaRaw,
  modelo,
  efectivo: parseMoney(c2),
  transferencia: parseMoney(c3),
  tarjeta: parseMoney(c4),
  searchText,
  searchCompact,
  modelText,
  modelCompact,
};


        // fila siguiente: colores
        const next = rows[i + 1]?.[0]?.trim() ?? '';
        if (next && isColors(next)) {
          producto.colores = cleanColors(next);
          i++;
        }

        productos.push(producto);
      }
    }

    return productos;
  }
}