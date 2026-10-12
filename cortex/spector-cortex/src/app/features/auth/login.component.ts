/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '@core/services/auth.service';
import { ThemeService } from '@core/services/theme.service';

/**
 * Authentication login view for Spector Cortex.
 *
 * Provides username/password credential submission to `/api/v1/auth/login`,
 * error display for invalid credentials, SSO redirect when OIDC is configured,
 * and automatic redirect to `returnUrl` (defaulting to `/dashboard`).
 */
@Component({
  selector: 'cortex-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loginForm: FormGroup = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(64)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hidePassword = signal(true);

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
      this.router.navigateByUrl(returnUrl);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { username, password } = this.loginForm.value;

    try {
      await this.auth.login(username.trim(), password);
      const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
      await this.router.navigateByUrl(returnUrl);
    } catch (err: any) {
      if (err?.status === 401) {
        this.errorMessage.set('Invalid credentials');
      } else if (err?.error?.message) {
        this.errorMessage.set(err.error.message);
      } else {
        this.errorMessage.set('Authentication failed. Please verify connection to Synapse.');
      }
    } finally {
      this.loading.set(false);
    }
  }

  loginWithSso(): void {
    const oidcUri = this.auth.authConfig()?.oidcIssuerUri;
    if (oidcUri) {
      window.location.href = oidcUri;
    } else {
      window.location.href = '/oauth2/authorization/oidc';
    }
  }
}
