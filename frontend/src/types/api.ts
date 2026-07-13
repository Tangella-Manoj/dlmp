// Mirrors com.dlmp.common.dto.ApiResponse<T> / ErrorResponse
export interface ApiResponse<T> {
  success: boolean;
  statusCode: number;
  message?: string;
  data: T;
  errorCode?: string;
  traceId?: string;
  timestamp: string;
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

export interface ErrorResponse {
  success: boolean;
  statusCode: number;
  message: string;
  errorCode?: string;
  path?: string;
  fieldErrors?: FieldError[];
  timestamp: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index (0-based)
  size: number;
  first: boolean;
  last: boolean;
}
