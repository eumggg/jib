import { ErrorRequestHandler, RequestHandler } from 'express';

export class HttpError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: unknown;

  constructor(status: number, code: string, message: string, details?: unknown) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

interface ErrorBody {
  error: { code: string; message: string; details?: unknown };
}

export const notFoundHandler: RequestHandler = (req, res) => {
  const body: ErrorBody = {
    error: { code: 'not_found', message: `Route ${req.method} ${req.path} not found` },
  };
  res.status(404).json(body);
};

// Express 4 requires a 4-arg signature for error middleware; the unused `_next`
// is intentional and matches the public ErrorRequestHandler contract.
export const errorHandler: ErrorRequestHandler = (err, _req, res, _next) => {
  if (res.headersSent) {
    return;
  }

  if (err instanceof HttpError) {
    const body: ErrorBody = {
      error: { code: err.code, message: err.message, details: err.details },
    };
    res.status(err.status).json(body);
    return;
  }

  if (process.env.NODE_ENV !== 'test') {
    console.error('[unhandled]', err);
  }

  const body: ErrorBody = {
    error: { code: 'internal_error', message: 'Internal Server Error' },
  };
  res.status(500).json(body);
};
