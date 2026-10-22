import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { App } from './app';

describe('App (shell)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), providePrimeNG({ theme: 'none' }), MessageService],
    }).compileComponents();
  });

  it('monta o shell com router-outlet e toast global', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('p-toast')).not.toBeNull();
  });
});
