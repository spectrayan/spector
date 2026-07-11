// ═══════════════════════════════════════════════════════════════════════
// Spector Cortex — Settings Component (Salience Profiles & ICNU Weights)
// ═══════════════════════════════════════════════════════════════════════

import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TextFieldModule } from '@angular/cdk/text-field';

import { SynapseApiService } from '../../core/services/synapse-api.service';
import { AuthService } from '../../core/services/auth.service';
import { ApiKeyService, ApiKeyInfo, ApiKeyCreatedResponse } from '../../core/services/api-key.service';
import { CortexSnackbarService } from '../../shared/services/cortex-snackbar.service';
import { ERROR_MESSAGES } from '../../shared/constants/error-messages';

interface InterestEntry {
  topic: string;
  level: string;
}

interface AiConfigField {
  key: string;
  defaultValue: any;
  type: string;
  description: string;
  editValue: any;
  source: 'system' | 'user';
}

@Component({
  selector: 'cortex-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatSliderModule,
    MatTooltipModule,
    MatDividerModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    TextFieldModule,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private readonly api = inject(SynapseApiService);
  private readonly toast = inject(CortexSnackbarService);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly apiKeyService = inject(ApiKeyService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly rescoring = signal(false);

  // Salience Profile
  readonly interests = signal<InterestEntry[]>([]);
  readonly disinterests = signal<InterestEntry[]>([]);
  readonly newInterestTopic = signal('');
  readonly newInterestLevel = signal('HIGH');
  readonly newDisinterestTopic = signal('');
  readonly newDisinterestLevel = signal('LOW');

  // ICNU Weights
  readonly icnuI = signal(0.25);
  readonly icnuC = signal(0.25);
  readonly icnuN = signal(0.25);
  readonly icnuU = signal(0.25);

  // Scoring
  readonly alpha = signal(0.6);
  readonly beta = signal(0.4);
  readonly flashbulbThreshold = signal(3.0);

  // Persona
  readonly about = signal('');
  readonly occupation = signal('');
  readonly nationality = signal('');
  readonly bigFiveO = signal(50);
  readonly bigFiveC = signal(50);
  readonly bigFiveE = signal(50);
  readonly bigFiveA = signal(50);
  readonly bigFiveN = signal(50);
  readonly eqSelfAwareness = signal(50);
  readonly eqSelfRegulation = signal(50);
  readonly eqMotivation = signal(50);
  readonly eqEmpathy = signal(50);
  readonly eqSocialSkills = signal(50);
  readonly stressResponse = signal('ADAPTIVE');
  readonly communicationStyle = signal('');
  readonly personaValues = signal<string[]>([]);
  readonly personaFears = signal<string[]>([]);
  readonly personaAspirations = signal<string[]>([]);
  readonly newValue = signal('');
  readonly newFear = signal('');
  readonly newAspiration = signal('');
  readonly personaLanguages = signal<string[]>([]);
  readonly newLanguage = signal('');
  readonly culturalEthnicity = signal('');
  readonly culturalRace = signal('');
  readonly culturalReligion = signal('');
  readonly culturalHeritage = signal('');
  readonly culturalPrimaryCulture = signal('');

  readonly stressResponseOptions = [
    { value: 'FIGHT', label: 'Fight — confrontational, aggressive' },
    { value: 'FLIGHT', label: 'Flight — avoidant, anxious' },
    { value: 'FREEZE', label: 'Freeze — paralysis, dissociation' },
    { value: 'FAWN', label: 'Fawn — people-pleasing, conflict-avoidant' },
    { value: 'ADAPTIVE', label: 'Adaptive — flexible, context-dependent' },
  ];

  readonly communicationStyleOptions = [
    { value: '', label: 'Not specified' },
    { value: 'ANALYTICAL', label: 'Analytical — data-driven, precise' },
    { value: 'INTUITIVE', label: 'Intuitive — big-picture, conceptual' },
    { value: 'FUNCTIONAL', label: 'Functional — process-oriented, structured' },
    { value: 'PERSONAL', label: 'Personal — emotionally aware, empathetic' },
    { value: 'DIRECT', label: 'Direct — concise, action-oriented' },
    { value: 'COLLABORATIVE', label: 'Collaborative — consensus-seeking' },
  ];

  // Profile metadata
  readonly profileScope = signal('user');
  readonly profileId = signal('default');

  // ═══ API Key Management ═══
  readonly apiKeys = signal<ApiKeyInfo[]>([]);
  readonly apiKeysLoading = signal(false);
  readonly showCreateKeyDialog = signal(false);
  readonly newKeyName = signal('');
  readonly newKeyExpiryDays = signal<number | null>(90);
  readonly newKeyScopes = signal<string[]>(['memory:read', 'memory:write']);
  readonly creatingKey = signal(false);
  readonly createdKey = signal<ApiKeyCreatedResponse | null>(null);
  readonly showCreatedKeyDialog = signal(false);
  readonly keyCopied = signal(false);
  readonly revokingKeyId = signal<string | null>(null);

  readonly expiryOptions = [
    { value: 7, label: '7 days' },
    { value: 30, label: '30 days' },
    { value: 60, label: '60 days' },
    { value: 90, label: '90 days' },
    { value: 365, label: '1 year' },
    { value: null, label: 'Never expires' },
  ];

  readonly scopeOptions = [
    { value: 'memory:read', label: 'Read memories', icon: 'visibility' },
    { value: 'memory:write', label: 'Write memories', icon: 'edit' },
    { value: 'memory:forget', label: 'Forget memories', icon: 'delete' },
  ];

  readonly interestLevels = [
    { value: 'CRITICAL', label: 'Critical (2.0×)', color: '#e74c3c' },
    { value: 'HIGH', label: 'High (1.5×)', color: '#f39c12' },
    { value: 'NORMAL', label: 'Normal (1.0×)', color: '#3498db' },
    { value: 'LOW', label: 'Low (0.5×)', color: '#95a5a6' },
    { value: 'IGNORE', label: 'Ignore (0.1×)', color: '#7f8c8d' },
  ];

  ngOnInit(): void {
    this.profileId.set(this.auth.currentUser()?.userId || 'default');
    this.loadProfile();
    this.loadAiConfig();
  }

  loadProfile(): void {
    this.loading.set(true);
    this.api.getSalienceProfile(this.profileScope(), this.profileId()).subscribe({
      next: (profile) => {
        // Parse interests/disinterests from the profile if available
        if (profile.interestsList) {
          this.interests.set(profile.interestsList);
        }
        if (profile.disinterestsList) {
          this.disinterests.set(profile.disinterestsList);
        }
        if (profile.icnuWeights) {
          this.icnuI.set(profile.icnuWeights.interest ?? 0.25);
          this.icnuC.set(profile.icnuWeights.challenge ?? 0.25);
          this.icnuN.set(profile.icnuWeights.novelty ?? 0.25);
          this.icnuU.set(profile.icnuWeights.urgency ?? 0.25);
        }
        if (profile.alpha != null) this.alpha.set(profile.alpha);
        if (profile.beta != null) this.beta.set(profile.beta);
        if (profile.flashbulbThreshold != null) this.flashbulbThreshold.set(profile.flashbulbThreshold);

        // Persona
        if (profile.persona) {
          const p = profile.persona;
          this.about.set(p.about ?? '');
          this.occupation.set(p.occupation ?? '');
          this.nationality.set(p.nationality ?? '');
          if (p.bigFive) {
            this.bigFiveO.set(p.bigFive.openness ?? 50);
            this.bigFiveC.set(p.bigFive.conscientiousness ?? 50);
            this.bigFiveE.set(p.bigFive.extraversion ?? 50);
            this.bigFiveA.set(p.bigFive.agreeableness ?? 50);
            this.bigFiveN.set(p.bigFive.neuroticism ?? 50);
          }
          if (p.emotionalIntelligence) {
            this.eqSelfAwareness.set(p.emotionalIntelligence.selfAwareness ?? 50);
            this.eqSelfRegulation.set(p.emotionalIntelligence.selfRegulation ?? 50);
            this.eqMotivation.set(p.emotionalIntelligence.motivation ?? 50);
            this.eqEmpathy.set(p.emotionalIntelligence.empathy ?? 50);
            this.eqSocialSkills.set(p.emotionalIntelligence.socialSkills ?? 50);
          }
          this.stressResponse.set(p.stressResponse ?? 'ADAPTIVE');
          this.communicationStyle.set(p.communicationStyle ?? '');
          this.personaValues.set(p.values ?? []);
          this.personaFears.set(p.fears ?? []);
          this.personaAspirations.set(p.aspirations ?? []);
          this.personaLanguages.set(p.languages ?? []);
          if (p.culturalIdentity) {
            this.culturalEthnicity.set(p.culturalIdentity.ethnicity ?? '');
            this.culturalRace.set(p.culturalIdentity.race ?? '');
            this.culturalReligion.set(p.culturalIdentity.religion ?? '');
            this.culturalHeritage.set(p.culturalIdentity.culturalHeritage ?? '');
            this.culturalPrimaryCulture.set(p.culturalIdentity.primaryCulture ?? '');
          }
        }

        this.loading.set(false);
      },
      error: () => {
        // Profile doesn't exist yet — start with empty
        this.loading.set(false);
      },
    });
  }

  addInterest(): void {
    const topic = this.newInterestTopic().trim();
    if (!topic) return;
    this.interests.update(list => [...list, { topic, level: this.newInterestLevel() }]);
    this.newInterestTopic.set('');
  }

  removeInterest(index: number): void {
    this.interests.update(list => list.filter((_, i) => i !== index));
  }

  addDisinterest(): void {
    const topic = this.newDisinterestTopic().trim();
    if (!topic) return;
    this.disinterests.update(list => [...list, { topic, level: this.newDisinterestLevel() }]);
    this.newDisinterestTopic.set('');
  }

  removeDisinterest(index: number): void {
    this.disinterests.update(list => list.filter((_, i) => i !== index));
  }

  getLevelColor(level: string): string {
    return this.interestLevels.find(l => l.value === level)?.color ?? '#999';
  }

  getLevelLabel(level: string): string {
    return this.interestLevels.find(l => l.value === level)?.label ?? level;
  }

  icnuSum(): number {
    return +(this.icnuI() + this.icnuC() + this.icnuN() + this.icnuU()).toFixed(2);
  }

  saveProfile(): void {
    this.saving.set(true);
    const profile: any = {
      interests: this.interests().reduce((acc, entry) => {
        acc[entry.topic] = entry.level;
        return acc;
      }, {} as Record<string, string>),
      disinterests: this.disinterests().reduce((acc, entry) => {
        acc[entry.topic] = entry.level;
        return acc;
      }, {} as Record<string, string>),
      icnuWeights: {
        interest: this.icnuI(),
        challenge: this.icnuC(),
        novelty: this.icnuN(),
        urgency: this.icnuU(),
      },
      alpha: this.alpha(),
      beta: this.beta(),
      flashbulbThreshold: this.flashbulbThreshold(),
    };

    // Include persona if any field is set
    if (this.about() || this.occupation() || this.nationality() || this.hasPersonaTraits()) {
      const persona: any = {};
      if (this.about()) persona.about = this.about();
      if (this.occupation()) persona.occupation = this.occupation();
      if (this.nationality()) persona.nationality = this.nationality();
      if (this.personaLanguages().length > 0) persona.languages = this.personaLanguages();
      if (this.personaValues().length > 0) persona.values = this.personaValues();
      if (this.personaFears().length > 0) persona.fears = this.personaFears();
      if (this.personaAspirations().length > 0) persona.aspirations = this.personaAspirations();

      persona.bigFive = {
        openness: this.bigFiveO(),
        conscientiousness: this.bigFiveC(),
        extraversion: this.bigFiveE(),
        agreeableness: this.bigFiveA(),
        neuroticism: this.bigFiveN(),
      };
      persona.emotionalIntelligence = {
        selfAwareness: this.eqSelfAwareness(),
        selfRegulation: this.eqSelfRegulation(),
        motivation: this.eqMotivation(),
        empathy: this.eqEmpathy(),
        socialSkills: this.eqSocialSkills(),
      };
      persona.stressResponse = this.stressResponse();
      if (this.communicationStyle()) persona.communicationStyle = this.communicationStyle();

      // Cultural Identity
      if (this.culturalEthnicity() || this.culturalRace() || this.culturalReligion()
        || this.culturalHeritage() || this.culturalPrimaryCulture()) {
        persona.culturalIdentity = {};
        if (this.culturalEthnicity()) persona.culturalIdentity.ethnicity = this.culturalEthnicity();
        if (this.culturalRace()) persona.culturalIdentity.race = this.culturalRace();
        if (this.culturalReligion()) persona.culturalIdentity.religion = this.culturalReligion();
        if (this.culturalHeritage()) persona.culturalIdentity.culturalHeritage = this.culturalHeritage();
        if (this.culturalPrimaryCulture()) persona.culturalIdentity.primaryCulture = this.culturalPrimaryCulture();
      }

      profile.persona = persona;
    }

    this.api.saveSalienceProfile(this.profileScope(), this.profileId(), profile).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(ERROR_MESSAGES.SETTINGS.SAVE_SUCCESS);
      },
      error: () => {
        this.saving.set(false);
        this.toast.error(ERROR_MESSAGES.SETTINGS.SAVE_FAILED);
      },
    });
  }

  triggerRescore(): void {
    this.rescoring.set(true);
    this.api.triggerRescore('BACKGROUND').subscribe({
      next: () => {
        this.rescoring.set(false);
        this.toast.success(ERROR_MESSAGES.SETTINGS.RESCORE_SUCCESS);
      },
      error: () => {
        this.rescoring.set(false);
        this.toast.error(ERROR_MESSAGES.SETTINGS.RESCORE_FAILED);
      },
    });
  }

  resetProfile(): void {
    this.interests.set([]);
    this.disinterests.set([]);
    this.icnuI.set(0.25);
    this.icnuC.set(0.25);
    this.icnuN.set(0.25);
    this.icnuU.set(0.25);
    this.alpha.set(0.6);
    this.beta.set(0.4);
    this.flashbulbThreshold.set(3.0);
    this.about.set('');
    this.occupation.set('');
    this.nationality.set('');
    this.bigFiveO.set(50); this.bigFiveC.set(50); this.bigFiveE.set(50);
    this.bigFiveA.set(50); this.bigFiveN.set(50);
    this.eqSelfAwareness.set(50); this.eqSelfRegulation.set(50);
    this.eqMotivation.set(50); this.eqEmpathy.set(50); this.eqSocialSkills.set(50);
    this.stressResponse.set('ADAPTIVE');
    this.communicationStyle.set('');
    this.personaValues.set([]); this.personaFears.set([]); this.personaAspirations.set([]);
    this.personaLanguages.set([]);
    this.culturalEthnicity.set(''); this.culturalRace.set('');
    this.culturalReligion.set(''); this.culturalHeritage.set('');
    this.culturalPrimaryCulture.set('');
  }

  private hasPersonaTraits(): boolean {
    return this.bigFiveO() !== 50 || this.bigFiveC() !== 50 || this.bigFiveE() !== 50
      || this.bigFiveA() !== 50 || this.bigFiveN() !== 50
      || this.eqSelfAwareness() !== 50 || this.eqSelfRegulation() !== 50
      || this.eqMotivation() !== 50 || this.eqEmpathy() !== 50 || this.eqSocialSkills() !== 50
      || this.personaValues().length > 0 || this.personaFears().length > 0
      || this.personaAspirations().length > 0
      || this.culturalEthnicity() !== '' || this.culturalRace() !== ''
      || this.culturalReligion() !== '' || this.culturalHeritage() !== '';
  }

  // ═══════════════════════════════════════════════════════════
  // AI Configuration (Hierarchical Config Overrides)
  // ═══════════════════════════════════════════════════════════

  readonly aiConfigLoading = signal(false);
  readonly aiConfigSaving = signal(false);
  readonly aiConfigDirty = signal(false);

  // Per-category annotated field data: { category: AnnotatedField[] }
  readonly aiConfigFields = signal<Record<string, AiConfigField[]>>({});
  readonly aiProviders = signal<{ name: string; displayName: string; supportsEmbedding: boolean; supportsGeneration: boolean }[]>([]);

  // Pending overrides to save
  private aiConfigOverrides: Record<string, Record<string, any>> = {};

  readonly aiSourceLabels: Record<string, string> = {
    system: '🔒 System',
    user: '👤 User',
  };

  readonly aiSourceColors: Record<string, string> = {
    system: '#7f8c8d',
    user: '#27ae60',
  };

  /** Loads annotated config for user-overridable categories. */
  loadAiConfig(): void {
    this.aiConfigLoading.set(true);
    this.aiConfigOverrides = {};
    this.aiConfigDirty.set(false);

    const categories = ['llm_provider', 'ingestion', 'rag'];
    let loaded = 0;
    const result: Record<string, AiConfigField[]> = {};

    // Load available providers
    this.api.listAvailableProviders().subscribe({
      next: (res) => this.aiProviders.set(res.providers || []),
      error: () => { },
    });

    for (const cat of categories) {
      // Load schema + annotated in sequence
      this.api.getConfigSchema(cat).subscribe({
        next: (schemaRes) => {
          this.api.getAnnotatedConfig(cat).subscribe({
            next: (annotatedRes) => {
              const fields: AiConfigField[] = [];
              for (const field of schemaRes.fields || []) {
                const entry = annotatedRes[field.key];
                fields.push({
                  key: field.key,
                  defaultValue: field.defaultValue,
                  type: field.type || 'string',
                  description: field.description || '',
                  editValue: entry?.value ?? field.defaultValue,
                  source: entry?.source ?? 'system',
                });
              }
              result[cat] = fields;
              loaded++;
              if (loaded === categories.length) {
                this.aiConfigFields.set(result);
                this.aiConfigLoading.set(false);
              }
            },
            error: () => {
              // Fall back to schema defaults
              result[cat] = (schemaRes.fields || []).map((f: any) => ({
                key: f.key,
                defaultValue: f.defaultValue,
                type: f.type || 'string',
                description: f.description || '',
                editValue: f.defaultValue,
                source: 'system' as const,
              }));
              loaded++;
              if (loaded === categories.length) {
                this.aiConfigFields.set(result);
                this.aiConfigLoading.set(false);
              }
            },
          });
        },
        error: () => {
          loaded++;
          if (loaded === categories.length) {
            this.aiConfigFields.set(result);
            this.aiConfigLoading.set(false);
          }
        },
      });
    }
  }

  onAiFieldChange(category: string, key: string, value: any): void {
    if (!this.aiConfigOverrides[category]) {
      this.aiConfigOverrides[category] = {};
    }
    this.aiConfigOverrides[category][key] = value;
    this.aiConfigDirty.set(true);

    // Update local display
    this.aiConfigFields.update((fields) => {
      const updated = { ...fields };
      if (updated[category]) {
        updated[category] = updated[category].map((f) =>
          f.key === key ? { ...f, editValue: value, source: 'user' as const } : f
        );
      }
      return updated;
    });
  }

  saveAiConfig(): void {
    this.aiConfigSaving.set(true);
    const categories = Object.keys(this.aiConfigOverrides);
    let saved = 0;

    if (categories.length === 0) {
      this.aiConfigSaving.set(false);
      return;
    }

    for (const cat of categories) {
      this.api.saveConfig(cat, 'user', this.aiConfigOverrides[cat]).subscribe({
        next: () => {
          saved++;
          if (saved === categories.length) {
            this.aiConfigSaving.set(false);
            this.aiConfigDirty.set(false);
            this.aiConfigOverrides = {};
            this.toast.success('AI configuration saved');
          }
        },
        error: (err) => {
          saved++;
          if (saved === categories.length) {
            this.aiConfigSaving.set(false);
          }
          this.toast.error(`Failed to save ${cat}: ${err.error?.error || err.message}`);
        },
      });
    }
  }

  resetAiConfig(): void {
    const categories = ['llm_provider', 'ingestion', 'rag'];
    let done = 0;
    for (const cat of categories) {
      this.api.deleteConfig(cat, 'user').subscribe({
        next: () => {
          done++;
          if (done === categories.length) {
            this.toast.success('AI settings reset to defaults');
            this.loadAiConfig();
          }
        },
        error: () => {
          done++;
          if (done === categories.length) {
            this.loadAiConfig();
          }
        },
      });
    }
  }

  // ── Persona chip-list helpers ──
  addChip(target: 'values' | 'fears' | 'aspirations' | 'languages'): void {
    const signalMap = { values: this.newValue, fears: this.newFear, aspirations: this.newAspiration, languages: this.newLanguage };
    const listMap = { values: this.personaValues, fears: this.personaFears, aspirations: this.personaAspirations, languages: this.personaLanguages };
    const val = signalMap[target]().trim();
    if (!val) return;
    listMap[target].update(l => [...l, val]);
    signalMap[target].set('');
  }

  removeChip(target: 'values' | 'fears' | 'aspirations' | 'languages', idx: number): void {
    const listMap = { values: this.personaValues, fears: this.personaFears, aspirations: this.personaAspirations, languages: this.personaLanguages };
    listMap[target].update(l => l.filter((_, i) => i !== idx));
  }

  // ═══════════════════════════════════════════════════════════
  // Privacy & Security
  // ═══════════════════════════════════════════════════════════

  readonly wipeMemories = signal(false);
  readonly wipeEntityGraph = signal(false);
  readonly wipeHebbian = signal(false);
  readonly wipeTemporal = signal(false);
  readonly wipePersona = signal(false);
  readonly wipeQueryHistory = signal(false);
  readonly wiping = signal(false);
  readonly wipeResult = signal<{ status: string; memoriesDeleted: number; categories: string[] } | null>(null);

  readonly deleteAllDataOnAccountDelete = signal(true);
  readonly deleteConfirmText = signal('');
  readonly deletingAccount = signal(false);

  /** Returns true if at least one wipe category is selected */
  get hasWipeSelection(): boolean {
    return this.wipeMemories() || this.wipeEntityGraph() || this.wipeHebbian()
      || this.wipeTemporal() || this.wipePersona() || this.wipeQueryHistory();
  }

  performWipe(): void {
    if (!this.hasWipeSelection) return;

    this.wiping.set(true);
    this.wipeResult.set(null);

    const body = {
      memories: this.wipeMemories(),
      entityGraph: this.wipeEntityGraph(),
      hebbianGraph: this.wipeHebbian(),
      temporalChain: this.wipeTemporal(),
      persona: this.wipePersona(),
      queryHistory: this.wipeQueryHistory(),
    };

    this.http.post<any>(`${environment.apiUrl}/privacy/wipe`, body).subscribe({
      next: (res) => {
        this.wiping.set(false);
        this.wipeResult.set(res);
        this.toast.success(`Data wiped: ${res.categories?.join(', ')}`);
        // Reset checkboxes
        this.wipeMemories.set(false);
        this.wipeEntityGraph.set(false);
        this.wipeHebbian.set(false);
        this.wipeTemporal.set(false);
        this.wipePersona.set(false);
        this.wipeQueryHistory.set(false);
      },
      error: (err) => {
        this.wiping.set(false);
        this.toast.error(`Wipe failed: ${err.error?.error || err.message}`);
      }
    });
  }

  // ═══════════════════════════════════════════════════════════════
  // API Key Management
  // ═══════════════════════════════════════════════════════════════

  loadApiKeys(): void {
    this.apiKeysLoading.set(true);
    this.apiKeyService.listApiKeys().subscribe({
      next: (keys) => {
        this.apiKeys.set(keys);
        this.apiKeysLoading.set(false);
      },
      error: (err) => {
        this.apiKeysLoading.set(false);
        this.toast.error(`Failed to load API keys: ${err.error?.error || err.message}`);
      }
    });
  }

  openCreateKeyDialog(): void {
    this.newKeyName.set('');
    this.newKeyExpiryDays.set(90);
    this.newKeyScopes.set(['memory:read', 'memory:write']);
    this.showCreateKeyDialog.set(true);
  }

  closeCreateKeyDialog(): void {
    this.showCreateKeyDialog.set(false);
  }

  createApiKey(): void {
    const name = this.newKeyName().trim();
    if (!name) {
      this.toast.error('Key name is required');
      return;
    }

    this.creatingKey.set(true);
    this.apiKeyService.createApiKey({
      name,
      expiresInDays: this.newKeyExpiryDays(),
      scopes: this.newKeyScopes(),
    }).subscribe({
      next: (created) => {
        this.creatingKey.set(false);
        this.showCreateKeyDialog.set(false);
        this.createdKey.set(created);
        this.showCreatedKeyDialog.set(true);
        this.keyCopied.set(false);
        this.loadApiKeys(); // refresh list
        this.toast.success('API key created successfully');
      },
      error: (err) => {
        this.creatingKey.set(false);
        this.toast.error(`Failed to create API key: ${err.error?.error || err.message}`);
      }
    });
  }

  copyKeyToClipboard(): void {
    const key = this.createdKey()?.key;
    if (key) {
      navigator.clipboard.writeText(key).then(() => {
        this.keyCopied.set(true);
        this.toast.success('API key copied to clipboard');
      });
    }
  }

  closeCreatedKeyDialog(): void {
    this.showCreatedKeyDialog.set(false);
    this.createdKey.set(null);
  }

  revokeApiKey(keyId: string): void {
    this.revokingKeyId.set(keyId);
    this.apiKeyService.revokeApiKey(keyId).subscribe({
      next: () => {
        this.revokingKeyId.set(null);
        this.loadApiKeys();
        this.toast.success('API key revoked');
      },
      error: (err) => {
        this.revokingKeyId.set(null);
        this.toast.error(`Failed to revoke key: ${err.error?.error || err.message}`);
      }
    });
  }

  toggleScope(scope: string): void {
    const current = this.newKeyScopes();
    if (current.includes(scope)) {
      this.newKeyScopes.set(current.filter(s => s !== scope));
    } else {
      this.newKeyScopes.set([...current, scope]);
    }
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return 'Never';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric', month: 'short', day: 'numeric'
    });
  }

  formatRelativeDate(dateStr: string | null): string {
    if (!dateStr) return 'Never used';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 30) return `${diffDays} days ago`;
    return this.formatDate(dateStr);
  }

  deleteAccount(): void {
    if (this.deleteConfirmText() !== 'DELETE') return;

    this.deletingAccount.set(true);

    this.http.delete<any>(`${environment.apiUrl}/privacy/account`, {
      body: { deleteAllData: this.deleteAllDataOnAccountDelete() }
    }).subscribe({
      next: () => {
        this.deletingAccount.set(false);
        this.toast.success('Account deleted. Logging out...');
        setTimeout(() => this.auth.logout(), 2000);
      },
      error: (err) => {
        this.deletingAccount.set(false);
        this.toast.error(`Account deletion failed: ${err.error?.error || err.message}`);
      }
    });
  }
}
