import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type CatalogCacheDateValue = string | number | null;

export interface CatalogCacheStatusResponse {
  initialized: boolean;
  fresh: boolean;
  stale: boolean;
  refreshing: boolean;
  version: number;
  productCount: number;
  lastSuccessfulRefreshAt?: CatalogCacheDateValue;
  lastAttemptAt?: CatalogCacheDateValue;
  expiresAt?: CatalogCacheDateValue;
  nextAllowedRefreshAt?: CatalogCacheDateValue;
  lastError?: string | null;
}

export interface CatalogCacheRefreshResponse {
  refreshStarted: boolean;
  alreadyRunning: boolean;
  blockedByCooldown: boolean;
  status: CatalogCacheStatusResponse;
}

@Injectable({
  providedIn: 'root'
})
export class CatalogCacheService {
  private readonly API_URL = 'https://www.cordobacelulares.com/api/admin/catalogo/cache';

  constructor(private http: HttpClient) { }

  getStatus(): Observable<CatalogCacheStatusResponse> {
    return this.http.get<CatalogCacheStatusResponse>(`${this.API_URL}/status`);
  }

  refresh(): Observable<CatalogCacheRefreshResponse> {
    return this.http.post<CatalogCacheRefreshResponse>(`${this.API_URL}/refresh`, {});
  }
}
