{{/*
Expand the name of the chart.
*/}}
{{- define "spector.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "spector.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "spector.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "spector.labels" -}}
helm.sh/chart: {{ include "spector.chart" . }}
{{ include "spector.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "spector.selectorLabels" -}}
app.kubernetes.io/name: {{ include "spector.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "spector.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "spector.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Cell ID helper
*/}}
{{- define "spector.cellId" -}}
{{- if and .Values.cell .Values.cell.id -}}
{{- .Values.cell.id -}}
{{- else -}}
cell-1
{{- end -}}
{{- end }}

{{/*
Owner set name (single-role preserves legacy name to prevent orphaning PVCs)
*/}}
{{- define "spector.owner.fullname" -}}
{{- if eq (default "split" .Values.topology.mode) "single-role" -}}
{{- include "spector.fullname" . -}}
{{- else -}}
{{- printf "%s-owner" (include "spector.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end }}

{{/*
Replica set name
*/}}
{{- define "spector.replica.fullname" -}}
{{- printf "%s-replica" (include "spector.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/*
Gateway deployment name
*/}}
{{- define "spector.gateway.fullname" -}}
{{- printf "%s-gateway" (include "spector.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}


