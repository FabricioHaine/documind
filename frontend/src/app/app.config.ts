import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import { routes } from './app.routes';
import { authInterceptor } from './core/http.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // PrimeNG 22 usa motion via CSS (@primeuix/motion) — sem Angular animations.
    providePrimeNG({
      ripple: false,
      // Tema dark fixo: o produto é dark-only (design premium do portfólio).
      theme: { preset: Aura, options: { darkModeSelector: '.app-dark' } },
    }),
  ],
};
