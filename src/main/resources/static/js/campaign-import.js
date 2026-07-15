/**
 * Campaign Package Import Dialog
 * Sends the selected file as raw body via fetch (no multipart), renders preview,
 * accepts warnings, and confirms import.
 */
document.addEventListener('alpine:init', () => {
    Alpine.data('campaignImport', () => ({
        open: false,
        file: null,
        uploading: false,
        preview: null,
        error: null,
        acceptWarnings: false,
        confirming: false,

        init() {
            window.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && this.open) this.close();
            });
        },

        async openDialog() {
            this.open = true;
            this.file = null;
            this.preview = null;
            this.error = null;
            this.acceptWarnings = false;
            this.confirming = false;
            document.getElementById('import-file-input').value = '';
            setTimeout(() => document.getElementById('import-dialog-title')?.focus(), 50);
        },

        close() {
            if (this.preview && this.preview.previewId) {
                fetch('/campaigns/package-imports/' + this.preview.previewId, { method: 'DELETE' })
                    .catch(() => {});
            }
            this.open = false;
        },

        fileSelected(e) {
            this.file = e.target.files[0];
            this.error = null;
            this.preview = null;
            if (this.file) this.upload();
        },

        async upload() {
            if (!this.file) return;
            this.uploading = true;
            this.error = null;
            try {
                const mediaType = this.file.name.endsWith('.dmcampaign') && !this.file.name.endsWith('.dmcampaign.json')
                    ? 'application/vnd.dmhelper.campaign+zip'
                    : 'application/json';
                const resp = await fetch('/campaigns/package-imports/previews', {
                    method: 'POST',
                    headers: {
                        'Content-Type': mediaType,
                        'X-DMHelper-Filename': this.file.name
                    },
                    body: this.file
                });
                this.preview = await resp.json();
                if (this.preview.status === 'BLOCKED') {
                    this.error = 'Import blocked due to validation errors.';
                }
            } catch (err) {
                this.error = 'Network error: ' + err.message;
            } finally {
                this.uploading = false;
            }
        },

        async confirm() {
            if (!this.preview || !this.preview.previewId) return;
            this.confirming = true;
            try {
                const resp = await fetch(
                    '/campaigns/package-imports/' + this.preview.previewId + '/confirm?acceptWarnings=' + this.acceptWarnings,
                    { method: 'POST' }
                );
                if (resp.ok) {
                    window.location.href = resp.headers.get('Location') || '/campaigns';
                } else if (resp.status === 400) {
                    const data = await resp.json();
                    this.error = data.detail || 'Confirmation failed.';
                } else {
                    this.error = 'Confirmation failed.';
                }
            } catch (err) {
                this.error = 'Network error: ' + err.message;
                this.confirming = false;
            }
        }
    }));
});
