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
  tarjeta1Pago: number | null;
  qr1Pago: number | null;
  planZ3Pagos: number | null;
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
  tarjeta1Pago: number | null;
  qr1Pago: number | null;
  planZ3Pagos: number | null;
  tarjeta3Pagos: number;
  tarjeta6Pagos: number;
  tarjeta12Pagos: number;
}

export interface LiberadosYaMarkupByTransferScheme {
  general: number | null;
  net7Net10: number | null;
}

export type LiberadosYaQuotationStatus = 'CURRENT' | 'STALE' | 'UNAVAILABLE';

export interface LiberadosYaQuotationResponse {
  obtainedAt: ApiDateValue;
  lastAttemptAt: ApiDateValue;
  updateStatus: LiberadosYaQuotationStatus;
  stale: boolean;
  sourceAppJsUrl: string | null;
  dolarBillete: number | null;
  usdt: number | null;
  transferencia: LiberadosYaMarkupByTransferScheme | null;
  tarjeta1Pago: LiberadosYaMarkupByTransferScheme | null;
  qr1Pago: LiberadosYaMarkupByTransferScheme | null;
  planZ3Pagos: LiberadosYaMarkupByTransferScheme | null;
  tarjeta3Pagos: LiberadosYaMarkupByTransferScheme | null;
  tarjeta6Pagos: LiberadosYaMarkupByTransferScheme | null;
  errorMessage: string | null;
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

export interface LiberadosYaCatalogStatus {
  available: boolean;
  refreshing: boolean;
  phase: string;
  processed_products: number;
  total_products: number;
  percent: number;
  current_product: string | null;
  generated_at: ApiDateValue;
  total_urls_found: number;
  errors_count: number;
  last_successful_refresh_at: ApiDateValue;
  last_attempt_at: ApiDateValue;
  last_error: string | null;
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
  resultStatus: ComparativaOverallStatus;
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

export type ProductContentStatus = 'DRAFT' | 'PUBLISHED' | null;

export interface ProductContentListItem {
  seoCatalogProductId: number;
  marca: string;
  modelo: string;
  slug: string;
  active: boolean;
  status: ProductContentStatus;
  imageCount: number;
  updatedAt: string | null;
  hasUnpublishedChanges: boolean;
}

export interface ProductContentPageResponse {
  data: ProductContentListItem[];
  brands: string[];
  page: number;
  limit: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ProductContentHighlight {
  id?: number | null;
  text: string;
  sortOrder?: number;
}

export interface ProductContentSpec {
  id?: number | null;
  label: string;
  value: string;
  sortOrder?: number;
}

export interface ProductContentSpecSection {
  id?: number | null;
  title: string;
  sortOrder?: number;
  specs: ProductContentSpec[];
}

export interface ProductContentImage {
  id: number;
  publicPath: string;
  originalFilename: string;
  altText: string;
  sortOrder: number;
  primaryImage: boolean;
  createdAt: string | null;
}

export interface ProductContentDetail {
  seoCatalogProductId: number;
  marca: string;
  modelo: string;
  slug: string;
  active: boolean;
  status: ProductContentStatus;
  description: string;
  sourceUrl: string;
  sourceName: string;
  highlights: ProductContentHighlight[];
  specSections: ProductContentSpecSection[];
  images: ProductContentImage[];
  createdAt: string | null;
  updatedAt: string | null;
  publishedAt: string | null;
  hasUnpublishedChanges: boolean;
}

export interface ProductContentSaveRequest {
  description: string;
  sourceUrl: string;
  sourceName: string;
  highlights: Array<{ id?: number | null; text: string }>;
  specSections: Array<{
    id?: number | null;
    title: string;
    specs: Array<{ id?: number | null; label: string; value: string }>;
  }>;
}

export interface PorteProductSyncStatus {
  running: boolean;
  state: string;
  trigger: string | null;
  startedAt: ApiDateValue;
  finishedAt: ApiDateValue;
  totalReferences: number;
  totalProducts: number;
  processedProducts: number;
  imported: number;
  updated: number;
  unchanged: number;
  withoutSheet: number;
  errors: number;
  currentReferenceId: number | null;
  currentSlug: string | null;
  lastError: string | null;
  automaticEnabled: boolean;
  automaticIntervalMs: number;
  automaticInitialDelayMs: number;
  batchSize: number;
  maxConcurrency: number;
  maxRetries: number;
}

export interface PorteProductSyncStartResponse {
  started: boolean;
  message: string;
  status: PorteProductSyncStatus;
}

export interface PorteBulkPublishPreview {
  eligibleToPublish: number;
  alreadyPublished: number;
  withoutValidPorteSheet: number;
  inactiveProducts: number;
  skippedOtherReasons: number;
}

export interface PorteBulkPublishError {
  slug: string;
  message: string;
}

export interface PorteBulkPublishResponse {
  totalEvaluated: number;
  published: number;
  skipped: number;
  errors: number;
  errorDetails: PorteBulkPublishError[];
}
