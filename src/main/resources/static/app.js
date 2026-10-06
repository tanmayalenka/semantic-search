(() => {
    'use strict';

    const $ = (id) => document.getElementById(id);

    const el = {
        query:      $('query'),
        limit:      $('limit'),
        minScore:   $('min-score'),
        searchBtn:  $('search-btn'),
        refreshBtn: $('refresh-btn'),
        refreshLbl: $('refresh-label'),
        statsLine:  $('stats-line'),
        status:     $('status'),
        results:    $('results'),
    };

    let currentMode = 'hybrid';

    // ---- API ----

    const api = {
        async search(q, limit, minScore, mode) {
            const params = new URLSearchParams({ q, limit, minScore, mode });
            return request(`/api/opportunities/search?${params}`);
        },
        async backfill(batchSize, maxBatches) {
            const params = new URLSearchParams({ batchSize, maxBatches });
            return request(`/api/admin/ingestion/backfill-embeddings?${params}`, {
                method: 'POST',
            });
        },
    };

    async function request(url, opts) {
        const res = await fetch(url, opts);
        if (!res.ok) {
            let detail = `${res.status} ${res.statusText}`;
            try {
                const body = await res.json();
                if (body.detail) detail = body.detail;
                else if (body.message) detail = body.message;
            } catch { /* non-JSON body, keep status text */ }
            throw new Error(detail);
        }
        return res.json();
    }

    // ---- Status banner ----

    function setStatus(kind, text) {
        el.status.hidden = false;
        el.status.className = `status ${kind}`;
        el.status.textContent = text;
    }

    function clearStatus() {
        el.status.hidden = true;
    }

    // ---- Score rendering ----

    function scoreClass(score) {
        if (score >= 0.7) return 'high';
        if (score >= 0.5) return 'mid';
        if (score >= 0.35) return 'low';
        return 'weak';
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // ---- Results rendering ----

    function renderResults(response) {
        if (!response.results.length) {
            el.results.innerHTML = `
                <div class="empty">
                    <div class="big">∅</div>
                    <div>No matches above score ${response.minScore.toFixed(2)}</div>
                    <div style="margin-top:6px;font-size:12.5px">
                        Try lowering the min score, or a broader query.
                    </div>
                </div>`;
            return;
        }

        const header = document.createElement('div');
        header.className = 'results-header';
        header.textContent =
            `${response.returned} result${response.returned === 1 ? '' : 's'} ` +
            `· ${response.tookMs} ms · ${response.mode} · limit ${response.limit}`;

        const cards = response.results.map(renderCard);
        el.results.replaceChildren(header, ...cards);
    }

    function renderCard(r) {
        const card = document.createElement('article');
        card.className = 'result-card';

        const metaParts = [r.department, r.subTier, r.office]
            .filter(Boolean)
            .map(escapeHtml);

        const footerParts = [];
        if (r.type)         footerParts.push(`<span class="tag">${escapeHtml(r.type)}</span>`);
        if (r.solicitation) footerParts.push(`<span class="mono">Sol: ${escapeHtml(r.solicitation)}</span>`);
        if (r.noticeId)     footerParts.push(`<span class="mono">${escapeHtml(r.noticeId)}</span>`);

        const matchTag = r.matchedBy
            ? `<span class="match-tag ${r.matchedBy}">${r.matchedBy}</span>`
            : '';

        card.innerHTML = `
            <div class="result-top">
                <div class="result-title">${escapeHtml(r.title)}${matchTag}</div>
                <div class="score-badge ${scoreClass(r.score)}">${r.score.toFixed(3)}</div>
            </div>
            ${metaParts.length
            ? `<div class="result-meta">${metaParts.join('<span class="sep">·</span>')}</div>`
            : ''}
            ${footerParts.length
            ? `<div class="result-footer">${footerParts.join('')}</div>`
            : ''}
        `;
        return card;
    }

    // ---- Search flow ----

    async function doSearch() {
        const q = el.query.value.trim();
        if (!q) {
            setStatus('error', 'Please enter a search query.');
            el.query.focus();
            return;
        }

        el.searchBtn.disabled = true;
        el.searchBtn.textContent = 'Searching…';
        setStatus('info', 'Embedding query and searching…');

        try {
            const limit = parseInt(el.limit.value, 10) || 20;
            const minScore = parseFloat(el.minScore.value) || 0;
            const response = await api.search(q, limit, minScore, currentMode);
            clearStatus();
            renderResults(response);
        } catch (err) {
            setStatus('error', `Search failed: ${err.message}`);
            el.results.replaceChildren();
        } finally {
            el.searchBtn.disabled = false;
            el.searchBtn.textContent = 'Search';
        }
    }

    // ---- Refresh flow ----

    async function doRefresh() {
        // Bounded per click so the request never runs long enough to be
        // killed by a proxy or browser timeout. 64 × 50 = 3200 per click,
        // roughly 1–3 minutes on CPU-only Ollama.
        const BATCH_SIZE = 64;
        const MAX_BATCHES = 50;

        el.refreshBtn.disabled = true;
        const started = Date.now();
        const timer = setInterval(() => {
            const secs = Math.floor((Date.now() - started) / 1000);
            el.refreshLbl.innerHTML = `<span class="spinner"></span>Embedding… ${secs}s`;
        }, 1000);

        setStatus('info',
            `Processing up to ${BATCH_SIZE * MAX_BATCHES} records. This can take a few minutes — leave the tab open.`);

        try {
            const result = await api.backfill(BATCH_SIZE, MAX_BATCHES);
            const secs = ((Date.now() - started) / 1000).toFixed(1);

            if (result.rowsProcessed === 0) {
                setStatus('success', `Nothing to embed — all records are up to date.`);
            } else {
                setStatus('success',
                    `Embedded ${result.rowsProcessed.toLocaleString()} record${result.rowsProcessed === 1 ? '' : 's'} in ${secs}s. ` +
                    `Click again if more remain.`);
            }
        } catch (err) {
            setStatus('error', `Embedding refresh failed: ${err.message}`);
        } finally {
            clearInterval(timer);
            el.refreshLbl.textContent = 'Refresh embeddings';
            el.refreshBtn.disabled = false;
        }
    }

    // ---- Wiring ----

    el.searchBtn.addEventListener('click', doSearch);

    el.query.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') doSearch();
    });

    document.querySelectorAll('.chip').forEach((chip) => {
        chip.addEventListener('click', () => {
            el.query.value = chip.dataset.query;
            el.query.focus();
        });
    });

    document.querySelectorAll('.mode-btn').forEach((btn) => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.mode-btn').forEach((b) => b.classList.remove('active'));
            btn.classList.add('active');
            currentMode = btn.dataset.mode;
            if (el.query.value.trim()) doSearch();
        });
    });

    el.refreshBtn.addEventListener('click', doRefresh);
})();