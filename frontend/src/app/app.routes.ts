import { Routes } from '@angular/router';
import { anonimoGuard, authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'documentos' },
  {
    path: 'login',
    canActivate: [anonimoGuard],
    loadComponent: () => import('./pages/login/login').then(m => m.LoginPage),
    title: 'DocuMind — Entrar',
  },
  {
    path: 'registrar',
    canActivate: [anonimoGuard],
    loadComponent: () => import('./pages/register/register').then(m => m.RegisterPage),
    title: 'DocuMind — Criar conta',
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/layout').then(m => m.Layout),
    children: [
      {
        path: 'documentos',
        loadComponent: () => import('./pages/documentos/documentos').then(m => m.DocumentosPage),
        title: 'DocuMind — Documentos',
      },
      {
        path: 'colecoes',
        loadComponent: () => import('./pages/colecoes/colecoes').then(m => m.ColecoesPage),
        title: 'DocuMind — Coleções',
      },
      {
        path: 'conversas',
        loadComponent: () => import('./pages/conversas/conversas').then(m => m.ConversasPage),
        title: 'DocuMind — Conversas',
      },
      {
        path: 'conversas/:id',
        loadComponent: () => import('./pages/conversas/conversa-detalhe/conversa-detalhe').then(m => m.ConversaDetalhePage),
        title: 'DocuMind — Chat',
      },
      {
        path: 'busca',
        loadComponent: () => import('./pages/busca/busca').then(m => m.BuscaPage),
        title: 'DocuMind — Pesquisa',
      },
    ],
  },
  { path: '**', redirectTo: 'documentos' },
];
