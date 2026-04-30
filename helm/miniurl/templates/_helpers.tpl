{{- define "miniurl.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .name }}
app.kubernetes.io/part-of: miniurl
app.kubernetes.io/managed-by: {{ .managedBy | default "helm" }}
{{- end }}

{{- define "miniurl.image" -}}
{{- $svc := .svc -}}
{{- $root := .root -}}
{{- $imageTag := "" -}}
{{- if $root.Values.globalConfig -}}
{{- $imageTag = index $root.Values.globalConfig "IMAGE_TAG" -}}
{{- end -}}
{{- if $imageTag -}}
{{ $svc.image.repository }}:{{ $imageTag }}
{{- else -}}
{{ $svc.image.repository }}:{{ $svc.image.tag | default "latest" }}
{{- end -}}
{{- end }}

{{- define "miniurl.serviceName" -}}
{{- if .canary -}}
{{ .name }}-canary
{{- else -}}
{{ .name }}
{{- end -}}
{{- end }}
