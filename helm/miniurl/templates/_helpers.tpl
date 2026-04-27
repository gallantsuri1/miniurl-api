{{- define "miniurl.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .name }}
app.kubernetes.io/part-of: miniurl
{{- end }}
