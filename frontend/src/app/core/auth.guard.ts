import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Só entra com sessão válida; sem sessão volta para o login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.autenticado()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

/** Área pública: autenticado vai para a home. */
export const anonimoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.autenticado()) {
    return true;
  }
  return router.createUrlTree(['/documentos']);
};
