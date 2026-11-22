import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import {
  AdminCatalogPageResponse,
  AdminCatalogQuery,
  BlockedCatalogProductResponse,
  CatalogCacheStatus,
  CatalogRefreshResponse,
  ComparativaResponse,
  CredentialCreateRequest,
  CredentialPatchRequest,
  CredentialResponse,
  LiberadosYaQuotationResponse,
  PorteProductSyncStartResponse,
  PorteProductSyncStatus,
  PriceConfigurationRequest,
  PriceConfigurationResponse
  , ProductContentDetail
  , ProductContentPageResponse
  , ProductContentSaveRequest
} from '../models/admin-config.models';

export class AdminConfigHttpError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

@Injectable({
  providedIn: 'root'
})
export class AdminConfigService {
  private readonly apiUrl = '/api';

  constructor(private http: HttpClient) { }

  getCredentials(): Observable<CredentialResponse[]> {
    return this.http.get<CredentialResponse[]>(`${this.apiUrl}/tiendaporte-credentials`)
      .pipe(catchError(error => this.handleError(error)));
  }

  createCredential(request: CredentialCreateRequest): Observable<CredentialResponse> {
    return this.http.post<CredentialResponse>(`${this.apiUrl}/tiendaporte-credentials`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  updateCredential(id: number, request: CredentialCreateRequest): Observable<CredentialResponse> {
    return this.http.put<CredentialResponse>(`${this.apiUrl}/tiendaporte-credentials/${id}`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  patchCredential(id: number, request: CredentialPatchRequest): Observable<CredentialResponse> {
    return this.http.patch<CredentialResponse>(`${this.apiUrl}/tiendaporte-credentials/${id}`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  deleteCredential(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/tiendaporte-credentials/${id}`)
      .pipe(catchError(error => this.handleError(error)));
  }

  getPriceConfiguration(): Observable<PriceConfigurationResponse> {
    return this.http.get<PriceConfigurationResponse>(`${this.apiUrl}/price-configuration`)
      .pipe(catchError(error => this.handleError(error)));
  }

  createPriceConfiguration(request: PriceConfigurationRequest): Observable<PriceConfigurationResponse> {
    return this.http.post<PriceConfigurationResponse>(`${this.apiUrl}/price-configuration`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  updatePriceConfiguration(request: PriceConfigurationRequest): Observable<PriceConfigurationResponse> {
    return this.http.put<PriceConfigurationResponse>(`${this.apiUrl}/price-configuration`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  patchPriceConfiguration(request: Partial<PriceConfigurationRequest>): Observable<PriceConfigurationResponse> {
    return this.http.patch<PriceConfigurationResponse>(`${this.apiUrl}/price-configuration`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  deletePriceConfiguration(): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/price-configuration`)
      .pipe(catchError(error => this.handleError(error)));
  }

  getLiberadosYaQuotations(): Observable<LiberadosYaQuotationResponse> {
    return this.http.get<LiberadosYaQuotationResponse>(`${this.apiUrl}/config/bombai/liberadosya/cotizaciones`)
      .pipe(catchError(error => this.handleError(error)));
  }

  getAdminCatalogProducts(query: AdminCatalogQuery): Observable<AdminCatalogPageResponse> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('limit', query.limit)
      .set('origen', query.origen);

    if (query.texto) {
      params = params.set('texto', query.texto);
    }
    if (query.categoria) {
      params = params.set('categoria', query.categoria);
    }
    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    if (query.direction) {
      params = params.set('direction', query.direction);
    }
    if (query.stock) {
      params = params.set('stock', query.stock);
    }

    return this.http.get<AdminCatalogPageResponse>(`${this.apiUrl}/admin/catalogo/equipos`, { params })
      .pipe(catchError(error => this.handleError(error)));
  }

  getBlockedCatalogProducts(): Observable<BlockedCatalogProductResponse[]> {
    return this.http.get<BlockedCatalogProductResponse[]>(`${this.apiUrl}/admin/catalogo/bloqueados`)
      .pipe(catchError(error => this.handleError(error)));
  }

  blockCatalogProduct(externalProductId: number): Observable<BlockedCatalogProductResponse> {
    return this.http.post<BlockedCatalogProductResponse>(`${this.apiUrl}/admin/catalogo/bloqueados/${externalProductId}`, {})
      .pipe(catchError(error => this.handleError(error)));
  }

  unblockCatalogProduct(externalProductId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/admin/catalogo/bloqueados/${externalProductId}`)
      .pipe(catchError(error => this.handleError(error)));
  }

  getCatalogCacheStatus(): Observable<CatalogCacheStatus> {
    return this.http.get<CatalogCacheStatus>(`${this.apiUrl}/admin/catalogo/cache/status`)
      .pipe(catchError(error => this.handleError(error)));
  }

  refreshCatalogCache(): Observable<CatalogRefreshResponse> {
    return this.http.post<CatalogRefreshResponse>(`${this.apiUrl}/admin/catalogo/cache/refresh`, {})
      .pipe(catchError(error => this.handleError(error)));
  }

  getComparativa(): Observable<ComparativaResponse> {
    return this.http.get<ComparativaResponse>(`${this.apiUrl}/config/bombai/comparativa`)
      .pipe(catchError(error => this.handleError(error)));
  }

  getProductContents(query: {
    text: string;
    brand: string;
    state: string;
    page: number;
    limit: number;
  }): Observable<ProductContentPageResponse> {
    const params = new HttpParams()
      .set('text', query.text)
      .set('brand', query.brand)
      .set('state', query.state)
      .set('page', query.page)
      .set('limit', query.limit);
    return this.http.get<ProductContentPageResponse>(`${this.apiUrl}/admin/product-content/products`, { params })
      .pipe(catchError(error => this.handleError(error)));
  }

  getProductContent(slug: string): Observable<ProductContentDetail> {
    return this.http.get<ProductContentDetail>(`${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}`)
      .pipe(catchError(error => this.handleError(error)));
  }

  startPorteProductContentSync(): Observable<PorteProductSyncStartResponse> {
    return this.http.post<PorteProductSyncStartResponse>(
      `${this.apiUrl}/admin/product-content/porte/sync`, {}
    ).pipe(catchError(error => this.handleError(error)));
  }

  getPorteProductContentSyncStatus(): Observable<PorteProductSyncStatus> {
    return this.http.get<PorteProductSyncStatus>(`${this.apiUrl}/admin/product-content/porte/sync/status`)
      .pipe(catchError(error => this.handleError(error)));
  }

  syncProductContentFromPorte(slug: string): Observable<PorteProductSyncStartResponse> {
    return this.http.post<PorteProductSyncStartResponse>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/porte/sync`, {}
    ).pipe(catchError(error => this.handleError(error)));
  }

  saveProductContentDraft(slug: string, request: ProductContentSaveRequest): Observable<ProductContentDetail> {
    return this.http.put<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/draft`, request
    ).pipe(catchError(error => this.handleError(error)));
  }

  publishProductContent(slug: string): Observable<ProductContentDetail> {
    return this.http.post<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/publish`, {}
    ).pipe(catchError(error => this.handleError(error)));
  }

  unpublishProductContent(slug: string): Observable<ProductContentDetail> {
    return this.http.post<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/unpublish`, {}
    ).pipe(catchError(error => this.handleError(error)));
  }

  uploadProductImage(slug: string, file: File, altText: string): Observable<ProductContentDetail> {
    const form = new FormData();
    form.append('file', file);
    form.append('altText', altText);
    return this.http.post<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/images`, form
    ).pipe(catchError(error => this.handleError(error)));
  }

  updateProductImageAlt(slug: string, imageId: number, altText: string): Observable<ProductContentDetail> {
    return this.http.put<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/images/${imageId}`,
      { altText }
    ).pipe(catchError(error => this.handleError(error)));
  }

  reorderProductImages(slug: string, imageIds: number[]): Observable<ProductContentDetail> {
    return this.http.put<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/images/order`,
      { imageIds }
    ).pipe(catchError(error => this.handleError(error)));
  }

  setPrimaryProductImage(slug: string, imageId: number): Observable<ProductContentDetail> {
    return this.http.put<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/images/${imageId}/primary`, {}
    ).pipe(catchError(error => this.handleError(error)));
  }

  deleteProductImage(slug: string, imageId: number): Observable<ProductContentDetail> {
    return this.http.delete<ProductContentDetail>(
      `${this.apiUrl}/admin/product-content/products/${encodeURIComponent(slug)}/images/${imageId}`
    ).pipe(catchError(error => this.handleError(error)));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    return throwError(() => new AdminConfigHttpError(this.errorMessage(error), error.status));
  }

  private errorMessage(error: HttpErrorResponse): string {
    const body = error.error as { message?: unknown; error?: unknown } | string | null | undefined;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object') {
      if (typeof body.message === 'string' && body.message.trim()) {
        return body.message;
      }
      if (typeof body.error === 'string' && body.error.trim()) {
        return body.error;
      }
    }
    if (error.status === 0) {
      return 'No se pudo conectar con el backend.';
    }
    return 'No se pudo completar la operacion solicitada.';
  }
}
