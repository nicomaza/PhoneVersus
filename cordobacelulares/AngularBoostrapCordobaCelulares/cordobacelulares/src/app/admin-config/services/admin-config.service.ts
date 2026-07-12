import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import {
  AdminCatalogPageResponse,
  AdminCatalogQuery,
  CatalogCacheStatus,
  CatalogRefreshResponse,
  CredentialCreateRequest,
  CredentialPatchRequest,
  CredentialResponse,
  PriceConfigurationRequest,
  PriceConfigurationResponse
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
  private readonly apiUrl = 'http://localhost:8080/api';

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

  getCatalogCacheStatus(): Observable<CatalogCacheStatus> {
    return this.http.get<CatalogCacheStatus>(`${this.apiUrl}/admin/catalogo/cache/status`)
      .pipe(catchError(error => this.handleError(error)));
  }

  refreshCatalogCache(): Observable<CatalogRefreshResponse> {
    return this.http.post<CatalogRefreshResponse>(`${this.apiUrl}/admin/catalogo/cache/refresh`, {})
      .pipe(catchError(error => this.handleError(error)));
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
