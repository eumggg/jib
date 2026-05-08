// Wire DTO for /api/stations — matches the JIB-7 / JIB-14 contract exactly.
// camelCase, latitude/longitude as Double, connectorTypes as JSON array.
export interface Station {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  connectorTypes: string[];
  powerKw: number | null;
  networkOperator: string | null;
  isAvailable: boolean;
  // Phase 2 enrichments — only present on the bbox list response. Both fields
  // are optional on the type so the bare /:id and POST shapes stay unchanged.
  avgRating?: number | null;
  recentCheckInAt?: string | null;
}

// Request body for POST /api/stations. Client supplies UUID `id` as the
// idempotency key — re-POST with the same id and identical body returns 200.
export interface CreateStationDto {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  connectorTypes: string[];
  powerKw?: number | null;
  networkOperator?: string | null;
  isAvailable?: boolean;
}

export interface CheckIn {
  id: string;
  stationId: string;
  userId: string;
  rating: number | null;
  comment: string | null;
  createdAt: string;
}

export interface CreateCheckInDto {
  idempotencyKey: string;
  stationId: string;
  rating?: number;
  comment?: string;
}

export interface User {
  id: string;
  uid: string;
  email: string | null;
  displayName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Review {
  id: string;
  stationId: string;
  userId: string;
  rating: number;
  body: string | null;
  createdAt: string;
}

export interface CreateReviewDto {
  idempotencyKey: string;
  stationId: string;
  rating: number;
  body?: string;
}

export interface StationPhoto {
  id: string;
  stationId: string;
  userId: string;
  storageUrl: string;
  createdAt: string;
}

export interface CreatePhotoDto {
  idempotencyKey: string;
  stationId: string;
  storageUrl: string;
}

export type StationReportKind = 'broken' | 'closed' | 'incorrect_info';

export interface StationReport {
  id: string;
  stationId: string;
  userId: string;
  kind: StationReportKind;
  notes: string | null;
  createdAt: string;
}

export interface CreateReportDto {
  idempotencyKey: string;
  stationId: string;
  kind: StationReportKind;
  notes?: string;
}
