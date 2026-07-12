import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
  provideAppInitializer,
  inject,
} from '@angular/core';
import { provideRouter, withViewTransitions } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { ThemeService } from './core/services/theme.service';
import { MockDataService } from './core/services/mock-data.service';


export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withViewTransitions()),
    provideAnimationsAsync(),
    provideHttpClient(withFetch(), withInterceptors([])),

    // Initialize theme and mock data simulation on startup
    provideAppInitializer(() => {
      inject(ThemeService);
      inject(MockDataService);
    }),
  ],
};
