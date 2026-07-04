import { HttpInterceptorFn } from '@angular/common/http';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const apiKey = localStorage.getItem('spector_api_key') || 'spector-dev-key';

  const cloned = req.clone({
    setHeaders: {
      'X-API-Key': apiKey
    }
  });

  return next(cloned);
};
