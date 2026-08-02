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
        returnFocus: null,

        init() {
            window.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && this.open) this.close();
            });
        },

        async openDialog() {
            this.returnFocus = document.activeElement;
            this.open = true;
            this.file = null;
            this.preview = null;
            this.error = null;
            this.acceptWarnings = false;
            this.confirming = false;
            document.getElementById('import-file-input').value = '';
            const dialog = this.$root.querySelector('.dialog');
            if (dialog) window.dmOverlay.open(dialog);
            setTimeout(() => document.getElementById('importDialogTitle')?.focus(), 50);
        },

        async close() {
            if (this.preview && this.preview.previewId) {
                try {
                    const response = await fetch('/campaigns/package-imports/' + this.preview.previewId,
                        { method: 'DELETE' });
                    if (!response.ok) {
                        this.error = await this.responseError(response, 'Could not discard the staged import.');
                        return;
                    }
                } catch (err) {
                    this.error = 'Could not discard the staged import: ' + err.message;
                    return;
                }
            }
            this.open = false;
            this.preview = null;
            const dialog = this.$root.querySelector('.dialog');
            if (dialog) window.dmOverlay.close(dialog);
            setTimeout(() => this.returnFocus?.focus(), 0);
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
                if (!resp.ok) {
                    this.error = await this.responseError(resp, 'Package preview failed.');
                    return;
                }
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
                } else {
                    this.error = await this.responseError(resp, 'Confirmation failed.');
                }
            } catch (err) {
                this.error = 'Network error: ' + err.message;
            } finally {
                this.confirming = false;
            }
        },

        async responseError(response, fallback) {
            const contentType = response.headers.get('Content-Type') || '';
            if (contentType.includes('json')) {
                try {
                    const data = await response.json();
                    return data.detail || data.message || fallback;
                } catch (_) {
                    return fallback;
                }
            }
            return fallback;
        }
    }));
});
