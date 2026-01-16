import { CommonModule } from '@angular/common';
import { Component, OnDestroy, signal } from '@angular/core';
import { ListaPreciosService, ProductoLista } from '../../services/lista-precios.service';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';
import Fuse from 'fuse.js';

type GrupoMarca = { marca: string; items: ProductoLista[] };
@Component({
  selector: 'app-lista-precios',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './lista-precios.component.html',
  styleUrl: './lista-precios.component.css'
})
export class ListaPreciosComponent  implements OnDestroy {
  loading = true;

  searchCtrl = new FormControl<string>('', { nonNullable: true });

  private all: ProductoLista[] = [];
  grupos: GrupoMarca[] = [];

  private fuse?: Fuse<ProductoLista>;
  private sub?: Subscription;

  selected: ProductoLista | null = null;
  modalOpen = false;

  // tu número (ej: 549351xxxxxxxx)
  private readonly WHATSAPP_PHONE = '5493512129922';

  constructor(private service: ListaPreciosService) {
    this.service.getProductos().subscribe({
      next: (data) => {
        this.all = data;

    this.fuse = new Fuse(data, {
  includeScore: true,
  threshold: 0.45,
  ignoreLocation: true,
  minMatchCharLength: 2,
  keys: [
    { name: 'modelText', weight: 0.75 },
    { name: 'modelCompact', weight: 0.95 },
    { name: 'searchText', weight: 0.25 },
    { name: 'searchCompact', weight: 0.45 },
  ],
});


        this.applyFilter('');
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });

    this.sub = this.searchCtrl.valueChanges
      .pipe(debounceTime(120), distinctUntilChanged())
      .subscribe(q => this.applyFilter(q));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  fmtMoney(n?: number) {
    return n == null ? '-' : n.toLocaleString('es-AR');
  }

  openDetails(p: ProductoLista) {
    this.selected = p;
    this.modalOpen = true;
    document.body.classList.add('modal-open');
  }

  closeModal() {
    this.modalOpen = false;
    this.selected = null;
    document.body.classList.remove('modal-open');
  }

 abrirWhatsApp(p: ProductoLista) {
  const phone = this.WHATSAPP_PHONE.replace(/[^\d]/g, ''); // por si quedó algún + o espacio

  const msg =
`Hola! Quiero consultar disponibilidad del *${p.marca} ${p.modelo}*.
- Efectivo: $${this.fmtMoney(p.efectivo)}
- Transferencia: $${this.fmtMoney(p.transferencia)}
- Tarjeta: $${this.fmtMoney(p.tarjeta)}
Colores: ${(p.colores?.length ? p.colores.join(', ') : 'a confirmar')}`;

  const url = `https://wa.me/${phone}?text=${encodeURIComponent(msg)}`;

  const win = window.open(url, '_blank');
  if (!win) window.location.assign(url); // fallback si el popup fue bloqueado
}




private applyFilter(qRaw: string) {
  const qNorm = this.normalizeQ(qRaw ?? '');

  if (!qNorm) {
    this.grupos = this.groupByBrand(this.all);
  } else {
    const ranked = this.smartSearch(this.all, qRaw);
    this.grupos = this.groupByBrand(ranked);
  }

  // Volver arriba si el usuario estaba scrolleado
  // (umbral para que no sea molesto si ya estás arriba)
  if (window.scrollY > 120) {
    requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }
}



private groupByBrand(list: ProductoLista[]): GrupoMarca[] {
  const map = new Map<string, ProductoLista[]>();

  for (const p of list) {
    const key = p.marca || 'Sin marca';
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(p); // respeta el orden del listado que le pases
  }

  // IMPORTANTE: NO ordenar keys -> queda en orden de aparición (sheet)
  return Array.from(map.entries()).map(([marca, items]) => ({
    marca,
    items, // NO ordenar modelos tampoco, queda como viene del sheet
  }));
}



private normalizeQ(txt: string) {
  return (txt ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

private buildTokens(qNorm: string): string[] {
  const raw = qNorm.split(' ').filter(Boolean);
  const tokens: string[] = [];

  for (let i = 0; i < raw.length; i++) {
    const t = raw[i];
    const next = raw[i + 1];

    tokens.push(t);

    // combina "a 56" -> "a56"
    if (/^[a-z]$/.test(t) && /^\d{1,3}$/.test(next)) tokens.push(t + next);
    // combina "56 a" -> "56a" (por si escriben raro)
    if (/^\d{1,3}$/.test(t) && /^[a-z]$/.test(next)) tokens.push(t + next);
  }

  // token compacto total: "note 14 5g" -> "note145g"
  const compact = qNorm.replace(/\s/g, '');
  if (compact && compact !== qNorm) tokens.push(compact);

  return Array.from(new Set(tokens));
}

private levenshtein(a: string, b: string): number {
  if (a === b) return 0;
  if (!a) return b.length;
  if (!b) return a.length;

  const m = a.length, n = b.length;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));

  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + cost
      );
    }
  }
  return dp[m][n];
}

private similarity(a: string, b: string): number {
  if (!a || !b) return 0;
  if (a === b) return 1;

  const la = a.length, lb = b.length;
  const short = la <= lb ? a : b;
  const long  = la > lb ? a : b;

  // Substring "fuerte" SOLO si son bastante parecidos en largo
  // (evita que note145g matchee con "note")
  if (long.includes(short) && short.length >= 3 && (short.length / long.length) >= 0.75) {
    return 0.95;
  }

  const dist = this.levenshtein(a, b);
  return 1 - dist / Math.max(la, lb);
}

private tokenMatchesProduct(token: string, p: any): boolean {
  const t = token;
  const tCompact = t.replace(/\s/g, '');

  // Match directo compacto
  if (p.modelCompact?.includes(tCompact) || p.searchCompact?.includes(tCompact)) return true;

  // Token compuesto (letras+números) => NO comparar contra palabras sueltas
  // Ej: note145g, a17, s24ultra, etc.
  const isCompound = /[a-z]/.test(tCompact) && /\d/.test(tCompact) && tCompact.length >= 5;
  if (isCompound) {
    const s1 = this.similarity(tCompact, p.modelCompact ?? '');
    const s2 = this.similarity(tCompact, p.searchCompact ?? '');
    return Math.max(s1, s2) >= 0.82; // umbral más estricto
  }

  // Fuzzy por palabra (solo tokens simples)
  const haystack = `${p.modelText ?? ''} ${this.normalizeQ(p.marca ?? '')}`;
  const words = haystack.split(' ').filter(Boolean);

  let best = 0;
  for (const w of words) best = Math.max(best, this.similarity(t, w));
  return best >= 0.70;
}


private smartSearch(list: any[], qRaw: string): any[] {
  const qNorm = this.normalizeQ(qRaw);
  if (!qNorm) return list;

  const tokens = this.buildTokens(qNorm);

  // tokens “fuertes”: contienen letras y largo >= 2 (note, 5g, samsun, a17)
  const strong = tokens.filter(t => /[a-z]/.test(t) && t.length >= 2);
  const nums = tokens.filter(t => /^\d+$/.test(t));

  const qCompact = qNorm.replace(/\s/g, '');

  const scored: { p: any; score: number }[] = [];

  for (const p of list) {
    // REGLA CLAVE: si hay tokens fuertes, TODOS deben matchear
    if (strong.length && !strong.every(t => this.tokenMatchesProduct(t, p))) continue;

    let score = 0;

    // match compacto total: máxima prioridad
    if (qCompact && p.searchCompact?.includes(qCompact)) score += 1000;
    else if (p.searchText?.includes(qNorm)) score += 700;

    // score por tokens fuertes: premio por estar en modelo (más que en marca)
    for (const t of strong) {
      const tCompact = t.replace(/\s/g, '');
      if (p.modelCompact?.includes(tCompact)) score += 120;
      else if (p.searchCompact?.includes(tCompact)) score += 70;
      else {
        // fuzzy: buscar similitud contra palabras del modelo
        const words = (p.modelText ?? '').split(' ').filter(Boolean);
        let best = 0;
        for (const w of words) best = Math.max(best, this.similarity(t, w));
        score += best * 60; // 0..60
      }
    }

    // números ayudan a ordenar (14, 128, etc.) pero no dominan
    for (const t of nums) {
      if (p.modelText?.includes(t)) score += 15;
    }

    scored.push({ p, score });
  }

  scored.sort((a, b) => b.score - a.score);
  return scored.map(x => x.p);
}


 
}