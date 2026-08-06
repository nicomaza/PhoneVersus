export interface CredentialResponse {
  id: number;
  username: string;
  activa: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CredentialCreateRequest {
  username: string;
  password: string;
  activa: boolean;
}

export interface CredentialPatchRequest {
  username?: string;
  password?: string;
  activa?: boolean;
}

export interface PriceConfigurationResponse {
  id: number;
  dolarBillete: number | null;
  usdt: number | null;
  transferenciaBancaria: number | null;
  tarjeta3Pagos: number | null;
  tarjeta6Pagos: number | null;
  tarjeta12Pagos: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface PriceConfigurationRequest {
  dolarBillete: number;
  usdt: number;
  transferenciaBancaria: number;
  tarjeta3Pagos: number;
  tarjeta6Pagos: number;
  tarjeta12Pagos: number;
}

export interface AdminCatalogProductRow {
  id: number | null;
  marca: string;
  modelo: string;
  color: string;
  cantidad: number | null;
  origen: string;
  precioUsd: number | null;
  precioPesos: number | null;
  precioTransferenciaBancaria: number | null;
  precioTarjeta3Pagos: number | null;
  precioTarjeta6Pagos: number | null;
  precioTarjeta12Pagos: number | null;
}

export type ProductSortField =
  | 'id'
  | 'marca'
  | 'modelo'
  | 'color'
  | 'cantidad'
  | 'precioUsd'
  | 'precioPesos';

export interface AdminCatalogQuery {
  texto?: string;
  categoria?: string;
  page: number;
  limit: number;
  sort?: ProductSortField;
  direction?: 'asc' | 'desc';
  origen: 'TIENDA_PORTE';
  stock?: string;
}

export interface AdminCatalogPageResponse {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  data: AdminCatalogProductRow[];
}

export type ApiDateValue = string | number | null;

export interface BlockedCatalogProductResponse {
  id: number;
  marca: string;
  modelo: string;
  precioUsd: number | null;
  origen: string;
  blockedAt: ApiDateValue;
}

export interface CatalogCacheStatus {
  initialized: boolean;
  fresh: boolean;
  stale: boolean;
  refreshing: boolean;
  version: number;
  productCount: number;
  lastSuccessfulRefreshAt: ApiDateValue;
  lastAttemptAt: ApiDateValue;
  expiresAt: ApiDateValue;
  nextAllowedRefreshAt: ApiDateValue;
  lastError: string | null;
}

export interface CatalogRefreshResponse {
  refreshStarted: boolean;
  alreadyRunning: boolean;
  blockedByCooldown: boolean;
  status: CatalogCacheStatus;
}

export interface ComparativaMetric {
  average: number | null;
  median: number | null;
  min: number | null;
  max: number | null;
  count: number;
}

export interface ComparativaSummary {
  competitorTransferMarkup: ComparativaMetric;
  competitorCardMarkup: ComparativaMetric;
  competitorImplicitRateOnMyCost: ComparativaMetric;
  competitorCustomerExchangeRate: ComparativaMetric;
  totalProducts: number;
  matchedProducts: number;
  comparableProducts: number;
  mineCheaperCount: number;
  mineCheaperPercent: number;
  mineMoreExpensiveCount: number;
  mineMoreExpensivePercent: number;
  mixedCount: number;
  withoutMatchCount: number;
}

export interface ComparativaMinePrices {
  costUsd: number | null;
  priceUsd: number | null;
  cashArs: number | null;
  transferArs: number | null;
  cardArs: number | null;
}

export interface ComparativaCompetitorPrices {
  matched: boolean;
  name: string | null;
  cashArs: number | null;
  currentArs: number | null;
  listArs: number | null;
  transferArs: number | null;
  cardArs: number | null;
  cashUsd: number | null;
  currentUsd: number | null;
  listUsd: number | null;
  mainUsd: number | null;
}

export interface ComparativaDifferences {
  cashArs: number | null;
  transferArs: number | null;
  cardArs: number | null;
  usd: number | null;
}

export type ComparativaComparisonStatus = 'CHEAPER' | 'MORE_EXPENSIVE' | 'EQUAL' | 'NOT_AVAILABLE' | string;
export type ComparativaOverallStatus = 'CHEAPER_ALL' | 'MORE_EXPENSIVE_ALL' | 'MIXED' | 'COMPETITIVE' | 'INCOMPLETE' | string;

export interface ComparativaComparison {
  cash: ComparativaComparisonStatus;
  transfer: ComparativaComparisonStatus;
  card: ComparativaComparisonStatus;
  usd: ComparativaComparisonStatus;
  overall: ComparativaOverallStatus;
  comparableCount: number;
}

export interface ComparativaProduct {
  brand: string;
  model: string;
  fullName: string;
  origin: string | null;
  colorCount: number;
  matchStatus: string;
  matchMessage: string | null;
  mine: ComparativaMinePrices;
  competitor: ComparativaCompetitorPrices;
  differences: ComparativaDifferences;
  comparison: ComparativaComparison;
}

export interface ComparativaResponse {
  summary: ComparativaSummary;
  products: ComparativaProduct[];
}
