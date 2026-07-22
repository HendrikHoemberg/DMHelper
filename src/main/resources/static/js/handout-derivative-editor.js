(function () {
    'use strict';

    window.openDerivativeDialog = function (sourceId, campaignId) {
        const el = document.querySelector('[x-data="derivativeEditor()"]');
        if (el && el.__x) {
            el.__x.$data.openDialog(sourceId, campaignId);
        } else {
            // Fallback: dispatch an event that Alpine can pick up
            document.dispatchEvent(new CustomEvent('open-derivative-dialog', {
                detail: { sourceId, campaignId }
            }));
        }
    };

    document.addEventListener('alpine:init', () => {
        Alpine.data('derivativeEditor', () => ({
            open: false,
            sourceId: null,
            campaignId: null,
            image: null,
            title: '',
            sourceWidth: 0,
            sourceHeight: 0,
            cropX: 0,
            cropY: 0,
            cropWidth: 0,
            cropHeight: 0,
            redactions: [],
            saving: false,
            pointerState: null,

            init() {
                this.$watch('cropX', () => this.render());
                this.$watch('cropY', () => this.render());
                this.$watch('cropWidth', () => this.render());
                this.$watch('cropHeight', () => this.render());
                this.$watch('redactions', () => this.render(), { deep: true });
                document.addEventListener('open-derivative-dialog', (e) => {
                    this.openDialog(e.detail.sourceId, e.detail.campaignId);
                });
            },

            openDialog(sourceId, campaignId) {
                this.sourceId = sourceId;
                this.campaignId = campaignId;
                this.open = true;
                this.image = new Image();
                this.image.crossOrigin = 'anonymous';
                this.image.onload = () => {
                    this.sourceWidth = this.image.naturalWidth;
                    this.sourceHeight = this.image.naturalHeight;
                    this.cropWidth = this.sourceWidth;
                    this.cropHeight = this.sourceHeight;
                    this.cropX = 0;
                    this.cropY = 0;
                    this.title = '';
                    this.redactions = [];
                    this.setupCanvas();
                    this.render();
                };
                this.image.src = '/files/' + sourceId;
            },

            close() {
                this.open = false;
                this.image = null;
            },

            setupCanvas() {
                const canvas = document.getElementById('derivativeCanvas');
                if (!canvas) return;
                const container = canvas.parentElement;
                const maxW = container.clientWidth || 600;
                const scale = Math.min(1, maxW / this.sourceWidth);
                canvas.width = this.sourceWidth;
                canvas.height = this.sourceHeight;
                canvas.style.width = (this.sourceWidth * scale) + 'px';
                canvas.style.height = (this.sourceHeight * scale) + 'px';
            },

            render() {
                const canvas = document.getElementById('derivativeCanvas');
                if (!canvas || !this.image) return;
                const ctx = canvas.getContext('2d');
                if (!ctx) return;

                ctx.clearRect(0, 0, canvas.width, canvas.height);
                ctx.drawImage(this.image, this.cropX, this.cropY, this.cropWidth, this.cropHeight,
                    0, 0, this.cropWidth, this.cropHeight);

                ctx.fillStyle = 'rgba(0,0,0,0.5)';
                for (const rect of this.redactions) {
                    ctx.fillRect(rect.x - this.cropX, rect.y - this.cropY, rect.width, rect.height);
                }
            },

            onPointerDown(event) {
                const canvas = document.getElementById('derivativeCanvas');
                if (!canvas) return;
                const rect = canvas.getBoundingClientRect();
                const scaleX = canvas.width / rect.width;
                const scaleY = canvas.height / rect.height;
                const imgX = Math.round((event.clientX - rect.left) * scaleX);
                const imgY = Math.round((event.clientY - rect.top) * scaleY);
                this.pointerState = { startX: imgX, startY: imgY, currentX: imgX, currentY: imgY };
            },

            onPointerMove(event) {
                if (!this.pointerState) return;
                const canvas = document.getElementById('derivativeCanvas');
                if (!canvas) return;
                const rect = canvas.getBoundingClientRect();
                const scaleX = canvas.width / rect.width;
                const scaleY = canvas.height / rect.height;
                this.pointerState.currentX = Math.round((event.clientX - rect.left) * scaleX);
                this.pointerState.currentY = Math.round((event.clientY - rect.top) * scaleY);
            },

            onPointerUp(event) {
                if (!this.pointerState) return;
                const canvas = document.getElementById('derivativeCanvas');
                if (!canvas) return;
                const rect = canvas.getBoundingClientRect();
                const scaleX = canvas.width / rect.width;
                const scaleY = canvas.height / rect.height;
                const endX = Math.round((event.clientX - rect.left) * scaleX);
                const endY = Math.round((event.clientY - rect.top) * scaleY);

                const sx = Math.min(this.pointerState.startX, endX);
                const sy = Math.min(this.pointerState.startY, endY);
                const w = Math.max(1, Math.abs(endX - this.pointerState.startX));
                const h = Math.max(1, Math.abs(endY - this.pointerState.startY));

                const imgX = sx + this.cropX;
                const imgY = sy + this.cropY;

                this.redactions.push({ x: imgX, y: imgY, width: w, height: h });
                this.pointerState = null;
            },

            addRedaction() {
                this.redactions.push({
                    x: this.cropX + Math.round(this.cropWidth * 0.1),
                    y: this.cropY + Math.round(this.cropHeight * 0.1),
                    width: Math.round(this.cropWidth * 0.3),
                    height: Math.round(this.cropHeight * 0.1)
                });
            },

            removeRedaction(index) {
                this.redactions.splice(index, 1);
            },

            async save() {
                if (this.saving) return;
                this.saving = true;

                try {
                    const canvas = document.getElementById('derivativeCanvas');
                    if (!canvas) throw new Error('Canvas not found');
                    const cropCanvas = document.createElement('canvas');
                    cropCanvas.width = this.cropWidth;
                    cropCanvas.height = this.cropHeight;
                    const cropCtx = cropCanvas.getContext('2d');
                    if (!cropCtx) throw new Error('Could not get crop context');
                    cropCtx.drawImage(this.image, this.cropX, this.cropY, this.cropWidth, this.cropHeight,
                        0, 0, this.cropWidth, this.cropHeight);
                    for (const rect of this.redactions) {
                        cropCtx.fillStyle = '#000';
                        cropCtx.fillRect(rect.x - this.cropX, rect.y - this.cropY, rect.width, rect.height);
                    }

                    const blob = await new Promise((resolve, reject) => {
                        cropCanvas.toBlob((b) => {
                            if (b) resolve(b);
                            else reject(new Error('canvas.toBlob returned null'));
                        }, 'image/png');
                    });

                    const recipe = {
                        sourceWidth: this.sourceWidth,
                        sourceHeight: this.sourceHeight,
                        cropX: this.cropX,
                        cropY: this.cropY,
                        cropWidth: this.cropWidth,
                        cropHeight: this.cropHeight,
                        redactions: this.redactions.map(r => ({
                            x: r.x, y: r.y, width: r.width, height: r.height
                        }))
                    };

                    const formData = new FormData();
                    formData.append('file', blob, 'derivative.png');
                    formData.append('title', this.title || '');
                    formData.append('recipe', JSON.stringify(recipe));

                    const response = await dmRequest(
                        '/campaigns/' + this.campaignId + '/handouts/' + this.sourceId + '/derivatives',
                        { method: 'POST', body: formData }
                    );

                    const redirect = response.headers.get('HX-Redirect');
                    if (redirect) {
                        window.location.href = redirect;
                    } else {
                        window.location.reload();
                    }
                } catch (error) {
                    window.reportActionFailure('Could not create derivative', error, () => this.save());
                } finally {
                    this.saving = false;
                }
            }
        }));
    });
})();
