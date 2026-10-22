import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Toast } from 'primeng/toast';

/**
 * Shell mínimo: aplica o tema dark no documento e hospeda o router +
 * o Toast global (MessageService/ConfirmationService vem do injector raiz,
 * providos em app.config.ts).
 */
@Component({
  imports: [RouterOutlet, Toast],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  constructor() {
    document.documentElement.classList.add('app-dark');
  }
}
