export interface Station {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  connectorTypes: string[];
  powerKw: number;
  networkOperator: string | null;
  isAvailable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CheckIn {
  id: string;
  stationId: string;
  userId: string;
  rating: number | null;
  comment: string | null;
  createdAt: string;
}

export interface CreateStationDto {
  idempotencyKey: string;
  name: string;
  latitude: number;
  longitude: number;
  connectorTypes: string[];
  powerKw: number;
  networkOperator?: string;
}

export interface CreateCheckInDto {
  idempotencyKey: string;
  stationId: string;
  rating?: number;
  comment?: string;
}
