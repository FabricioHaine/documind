import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Usuario } from '../core/models';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-layout',
  styleUrl: './layout.scss',
  templateUrl: './layout.html',
})
export class Layout implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected usuario: Usuario | null = null;

  protected readonly menu = [
    { rota: '/documentos', label: 'Documentos', icone: 'pi-file' },
    { rota: '/colecoes', label: 'Coleções', icone: 'pi-folder' },
    { rota: '/conversas', label: 'Conversas', icone: 'pi-comments' },
    { rota: '/busca', label: 'Pesquisa', icone: 'pi-search' },
  ];

  ngOnInit(): void {
    void this.auth.sincronizarSessao().then(() => {
      this.usuario = this.auth.usuario();
    });
  }

  sair(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
