import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, finalize, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';

export interface TiendaPorteBrandResponse {
  marca?: string | null;
  modelos?: TiendaPorteModelResponse[] | null;
}

export type CatalogProductOrigin = 'TIENDA_PORTE' | 'GOOGLE_SHEET' | string;

export interface TiendaPorteModelResponse {
  modeloNombre?: string | null;
  showColorOnCard?: boolean;
  origen?: CatalogProductOrigin | null;
  source?: CatalogProductOrigin | boolean | null;
  origin?: CatalogProductOrigin | boolean | null;
  provider?: CatalogProductOrigin | boolean | null;
  proveedor?: CatalogProductOrigin | boolean | null;
  fromSupplierSheet?: boolean | string | null;
  fromGoogleSheet?: boolean | string | null;
  colores?: TiendaPorteColorStockResponse[] | null;
  precioUsd?: number | null;
  precioPesos?: number | null;
  precioUsdt?: number | null;
  precioTransferenciaBancaria?: number | null;
  precioTarjeta3Pagos?: number | null;
  precioTarjeta6Pagos?: number | null;
  precioTarjeta12Pagos?: number | null;
}

export interface TiendaPorteColorStockResponse {
  color?: string | null;
  stock?: number | null;
}

export interface TiendaPorteCategoryPageResponse {
  categoria?: string | null;
  page?: number | null;
  limit?: number | null;
  total?: number | null;
  totalPages?: number | null;
  hasNext?: boolean | null;
  data?: TiendaPorteBrandResponse[] | null;
}

export interface TiendaPorteCategoryPageState {
  page: number;
  totalPages: number;
  hasNext: boolean;
  loading: boolean;
}

export type LiberadosYaDynamicValue =
  | string
  | number
  | boolean
  | null
  | LiberadosYaDynamicValue[]
  | { [key: string]: LiberadosYaDynamicValue };

export type LiberadosYaDynamicRecord = Record<string, LiberadosYaDynamicValue>;

export interface LiberadosYaProductMatchResponse {
  nombre?: string | null;
  marca?: string | null;
  modelo?: string | null;
  version_comercial?: string | null;
  descripcion_texto?: string | null;
  ficha_resumida?: LiberadosYaDynamicRecord | null;
  ventajas?: string[] | null;
  especificaciones?: Record<string, LiberadosYaDynamicRecord | null | undefined> | null;
  imagen_principal?: string | null;
  imagenes?: string[] | null;
}

@Injectable({
  providedIn: 'root'
})
export class NuevosPreciosService {
  private readonly API_URL = '/api/tiendaporte/equipos';
  private readonly LIBERADOS_YA_PUBLIC_URL = '/api/public/products/liberadosya';

  private allCatalogCache: TiendaPorteBrandResponse[] | null = null;
  private allCatalogLoading$?: Observable<TiendaPorteBrandResponse[]>;

  private readonly categoryCache = new Map<string, TiendaPorteBrandResponse[]>();
  private readonly categoryPageState = new Map<string, TiendaPorteCategoryPageState>();
  private readonly categoryLoadedPages = new Map<string, Set<number>>();
  private readonly categoryRequests = new Map<string, Observable<TiendaPorteCategoryPageResponse>>();

  constructor(private http: HttpClient) {}

  getCatalogoPrincipal(): Observable<TiendaPorteBrandResponse[]> {
    if (this.allCatalogCache) {
      return of(this.allCatalogCache);
    }

    if (!this.allCatalogLoading$) {
      this.allCatalogLoading$ = this.http
        .get<TiendaPorteBrandResponse[] | null>(`${this.API_URL}/categorias-principales`)
        .pipe(
          map(data => this.normalizeBrands(data)),
          tap(data => {
            this.allCatalogCache = data;
          }),
          catchError(error => {
            this.allCatalogLoading$ = undefined;
            return throwError(() => error);
          }),
          shareReplay(1)
        );
    }

    return this.allCatalogLoading$;
  }

  hasCatalogCache(): boolean {
    return this.allCatalogCache !== null;
  }

  getCategoryFromCatalog(categoria: string): TiendaPorteBrandResponse[] {
    if (!this.allCatalogCache) {
      return [];
    }
    return this.filterBrandsByCategory(this.allCatalogCache, categoria);
  }

  getCachedCategory(categoria: string): TiendaPorteBrandResponse[] | null {
    return this.categoryCache.get(this.categoryKey(categoria)) ?? null;
  }

  getCategoryPageState(categoria: string): TiendaPorteCategoryPageState | null {
    const state = this.categoryPageState.get(this.categoryKey(categoria));
    return state ? { ...state } : null;
  }

  getCategoryPage(
    categoria: string,
    page = 1,
    limit = 50
  ): Observable<TiendaPorteCategoryPageResponse> {
    const key = this.categoryKey(categoria);
    const safePage = this.safePositiveInteger(page, 1);
    const safeLimit = this.safePositiveInteger(limit, 50);
    const state = this.ensureCategoryState(key);

    if (this.categoryLoadedPages.get(key)?.has(safePage)) {
      return of({
        categoria,
        page: safePage,
        limit: safeLimit,
        totalPages: state.totalPages,
        hasNext: state.hasNext,
        data: this.categoryCache.get(key) ?? []
      });
    }

    const requestKey = `${key}:${safePage}`;
    const activeRequest = this.categoryRequests.get(requestKey);
    if (activeRequest) {
      return activeRequest;
    }

    state.loading = true;

    const request$ = this.http
      .get<TiendaPorteCategoryPageResponse | null>(
        `${this.API_URL}/categoria/${encodeURIComponent(categoria)}?page=${safePage}&limit=${safeLimit}`
      )
      .pipe(
        map(response => this.normalizePageResponse(response, categoria, safePage, safeLimit)),
        tap(response => this.storeCategoryPage(key, safePage, response)),
        finalize(() => {
          state.loading = false;
          this.categoryRequests.delete(requestKey);
        }),
        shareReplay(1)
      );

    this.categoryRequests.set(requestKey, request$);
    return request$;
  }

  getLiberadosYaProductMatch(
    brand: string,
    model: string
  ): Observable<LiberadosYaProductMatchResponse> {
    const params = new HttpParams()
      .set('brand', brand)
      .set('model', model);

    return this.http.get<LiberadosYaProductMatchResponse>(
      `${this.LIBERADOS_YA_PUBLIC_URL}/match`,
      { params }
    );
  }

  private storeCategoryPage(
    key: string,
    page: number,
    response: TiendaPorteCategoryPageResponse
  ): void {
    const state = this.ensureCategoryState(key);
    const incoming = this.normalizeBrands(response.data);
    const current = page === 1 ? [] : (this.categoryCache.get(key) ?? []);
    const merged = this.mergeBrands(current, incoming);

    this.categoryCache.set(key, merged);

    state.page = this.safePositiveInteger(response.page ?? page, page);
    state.totalPages = this.safePositiveInteger(response.totalPages ?? state.page, state.page);
    state.hasNext = response.hasNext === true && state.page < state.totalPages;

    if (!this.categoryLoadedPages.has(key)) {
      this.categoryLoadedPages.set(key, new Set<number>());
    }
    this.categoryLoadedPages.get(key)!.add(page);
  }

  private normalizePageResponse(
    response: TiendaPorteCategoryPageResponse | null,
    categoria: string,
    page: number,
    limit: number
  ): TiendaPorteCategoryPageResponse {
    const normalizedPage = this.safePositiveInteger(response?.page ?? page, page);
    const totalPages = this.safePositiveInteger(response?.totalPages ?? normalizedPage, normalizedPage);

    return {
      categoria: response?.categoria ?? categoria,
      page: normalizedPage,
      limit: this.safePositiveInteger(response?.limit ?? limit, limit),
      total: this.safeNonNegativeInteger(response?.total),
      totalPages,
      hasNext: response?.hasNext === true && normalizedPage < totalPages,
      data: this.normalizeBrands(response?.data)
    };
  }

  private ensureCategoryState(key: string): TiendaPorteCategoryPageState {
    let state = this.categoryPageState.get(key);
    if (!state) {
      state = {
        page: 0,
        totalPages: 1,
        hasNext: false,
        loading: false
      };
      this.categoryPageState.set(key, state);
    }
    return state;
  }

  private filterBrandsByCategory(
    data: TiendaPorteBrandResponse[],
    categoria: string
  ): TiendaPorteBrandResponse[] {
    const target = this.categoryKey(categoria);
    return data.filter(brand => this.categoryKey(brand?.marca ?? '') === target);
  }

  private normalizeBrands(data: TiendaPorteBrandResponse[] | null | undefined): TiendaPorteBrandResponse[] {
    return Array.isArray(data) ? this.mergeBrands([], data) : [];
  }

  private mergeBrands(
    current: TiendaPorteBrandResponse[],
    incoming: TiendaPorteBrandResponse[]
  ): TiendaPorteBrandResponse[] {
    const brands = new Map<string, TiendaPorteBrandResponse>();
    const modelKeys = new Map<string, Map<string, TiendaPorteModelResponse>>();

    const addBrand = (brand: TiendaPorteBrandResponse): void => {
      const marca = (brand?.marca ?? '').trim() || 'Sin marca';
      const brandKey = this.categoryKey(marca) || marca;

      if (!brands.has(brandKey)) {
        brands.set(brandKey, { marca, modelos: [] });
        modelKeys.set(brandKey, new Map<string, TiendaPorteModelResponse>());
      }

      const target = brands.get(brandKey)!;
      const knownModels = modelKeys.get(brandKey)!;
      const modelos = Array.isArray(brand?.modelos) ? brand.modelos : [];

      for (const model of modelos) {
        for (const variant of this.expandModelVariants(model)) {
          const modelKey = this.modelVariantKey(variant);
          if (!modelKey) {
            continue;
          }

          const existing = knownModels.get(modelKey);
          if (existing) {
            const preferred = this.preferredModelOffer(existing, variant);
            const discarded = preferred === existing ? variant : existing;
            this.copyMissingOriginFields(preferred, discarded);

            if (preferred !== existing) {
              const index = (target.modelos ?? []).indexOf(existing);
              if (index >= 0) {
                target.modelos![index] = preferred;
              }
              knownModels.set(modelKey, preferred);
            }
            continue;
          }
          target.modelos = [...(target.modelos ?? []), variant];
          knownModels.set(modelKey, variant);
        }
      }
    };

    current.forEach(addBrand);
    incoming.forEach(addBrand);

    return Array.from(brands.values()).map(brand => ({
      ...brand,
      modelos: this.groupModelsForCards(brand.modelos ?? [])
    }));
  }

  private groupModelsForCards(models: TiendaPorteModelResponse[]): TiendaPorteModelResponse[] {
    const groups = new Map<string, TiendaPorteModelResponse[]>();

    for (const model of models) {
      const key = this.categoryKey(model?.modeloNombre ?? '');
      if (!key) {
        continue;
      }
      groups.set(key, [...(groups.get(key) ?? []), model]);
    }

    return Array.from(groups.values()).flatMap(variants => {
      const priceKeys = new Set(variants.map(variant => this.modelOfferPriceKey(variant)));
      if (priceKeys.size > 1) {
        return variants.map(variant => ({ ...variant, showColorOnCard: true }));
      }

      const preferred = variants.reduce((current, candidate) =>
        this.preferredModelOffer(current, candidate)
      );
      const grouped: TiendaPorteModelResponse = {
        ...preferred,
        showColorOnCard: false,
        colores: this.mergeVariantColors(variants)
      };
      variants.forEach(variant => this.copyMissingOriginFields(grouped, variant));
      return [grouped];
    });
  }

  private mergeVariantColors(variants: TiendaPorteModelResponse[]): TiendaPorteColorStockResponse[] {
    const colorsByKey = new Map<string, TiendaPorteColorStockResponse>();

    for (const variant of variants) {
      for (const color of variant?.colores ?? []) {
        const colorName = (color?.color ?? '').trim();
        const colorKey = this.categoryKey(colorName);
        if (!colorKey) {
          continue;
        }
        const stock = this.safeNonNegativeInteger(color?.stock);
        const existing = colorsByKey.get(colorKey);
        if (!existing || stock > this.safeNonNegativeInteger(existing.stock)) {
          colorsByKey.set(colorKey, { color: colorName, stock });
        }
      }
    }

    return Array.from(colorsByKey.values());
  }

  private expandModelVariants(model: TiendaPorteModelResponse): TiendaPorteModelResponse[] {
    const colorsByKey = new Map<string, TiendaPorteColorStockResponse>();
    const colors = Array.isArray(model?.colores) ? model.colores : [];

    for (const color of colors) {
      const colorName = (color?.color ?? '').trim();
      const colorKey = this.categoryKey(colorName);
      if (!colorKey) {
        continue;
      }

      const existing = colorsByKey.get(colorKey);
      const stock = this.safeNonNegativeInteger(color?.stock);
      if (!existing || stock > this.safeNonNegativeInteger(existing.stock)) {
        colorsByKey.set(colorKey, { color: colorName, stock });
      }
    }

    if (colorsByKey.size === 0) {
      return [model];
    }

    return Array.from(colorsByKey.values()).map(color => ({
      ...model,
      colores: [color]
    }));
  }

  private modelVariantKey(model: TiendaPorteModelResponse): string {
    const modelKey = this.categoryKey(model?.modeloNombre ?? '');
    if (!modelKey) {
      return '';
    }

    const colorKey = (model?.colores ?? [])
      .map(color => this.categoryKey(color?.color ?? ''))
      .filter(Boolean)
      .sort()
      .join('|');
    return `${modelKey}|color:${colorKey || 'sin-color'}`;
  }

  private preferredModelOffer(
    current: TiendaPorteModelResponse,
    candidate: TiendaPorteModelResponse
  ): TiendaPorteModelResponse {
    return this.modelOfferPrice(candidate) < this.modelOfferPrice(current) ? candidate : current;
  }

  private modelOfferPrice(model: TiendaPorteModelResponse): number {
    for (const value of [model?.precioUsd, model?.precioPesos]) {
      if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
        return value;
      }
    }
    return Number.POSITIVE_INFINITY;
  }

  private modelOfferPriceKey(model: TiendaPorteModelResponse): string {
    if (typeof model?.precioUsd === 'number' && Number.isFinite(model.precioUsd)) {
      return `USD:${model.precioUsd}`;
    }
    if (typeof model?.precioPesos === 'number' && Number.isFinite(model.precioPesos)) {
      return `ARS:${model.precioPesos}`;
    }
    return 'SIN_PRECIO';
  }

  private copyMissingOriginFields(
    target: TiendaPorteModelResponse,
    source: TiendaPorteModelResponse | null | undefined
  ): void {
    if (!source) {
      return;
    }

    target.origen ??= source.origen;
    target.source ??= source.source;
    target.origin ??= source.origin;
    target.provider ??= source.provider;
    target.proveedor ??= source.proveedor;
    target.fromSupplierSheet ??= source.fromSupplierSheet;
    target.fromGoogleSheet ??= source.fromGoogleSheet;
  }

  private categoryKey(value: string): string {
    return (value ?? '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\s]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private safePositiveInteger(value: number | null | undefined, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) && value > 0
      ? Math.floor(value)
      : fallback;
  }

  private safeNonNegativeInteger(value: number | null | undefined): number {
    return typeof value === 'number' && Number.isFinite(value) && value >= 0
      ? Math.floor(value)
      : 0;
  }
}
