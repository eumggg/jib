import { ErrorRequestHandler, RequestHandler } from 'express';

// Wire format from JIB-7 contract: {error: <machine-code>, message: <human-readable>}.
// Stable codes: not_found, invalid_bbox, unauthorized, forbidden, validation_failed,
// rate_limited, conflict, internal_error.
export class HttpError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.code = code;
  }
}

export interface ErrorBody {
  error: string;
  message: string;
}

export const notFoundHandler: RequestHandler = (req, res) => {
  const body: ErrorBody = {
    error: 'not_found',
    message: `Route ${req.method} ${req.path} not found`,
  };
  res.status(404).json(body);
};

// Express 4 requires the 4-arg signature for error middleware; the unused `_next`
// is intentional and matches the public ErrorRequestHandler contract.
export const errorHandler: ErrorRequestHandler = (err, _req, res, _next) => {
  if (res.headersSent) {
    return;
  }

  if (err instanceof HttpError) {
    const body: ErrorBody = { error: err.code, message: err.message };
    res.status(err.status).json(body);
    return;
  }

  if (process.env.NODE_ENV !== 'test') {
    console.error('[unhandled]', err);
  }

  const body: ErrorBody = { error: 'internal_error', message: 'Internal Server Error' };
  res.status(500).json(body);
};
