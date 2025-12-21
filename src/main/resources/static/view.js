document.addEventListener('DOMContentLoaded', () => {
    const btnUpdate = document.getElementById('btnUpdate');
    const btnAdd = document.getElementById('btnAdd');
    const btnDelete = document.getElementById('btnDelete');

    const brands = ["BMW", "MERCEDES", "TOYOTA", "TESLA", "HONDA"];

    function setCounts(stats) {
        for (const brand of brands) {
            const el = document.getElementById(`count-${brand}`);
            if (el) el.textContent = stats[brand] ?? 0;
        }
    }

    let chart;
    function ensureChart(stats) {
        const canvas = document.getElementById('carBrandChart');
        if (!canvas) return;
        if (!window.Chart) return;

        const data = brands.map(b => stats[b] ?? 0);

        if (!chart) {
            const ctx = canvas.getContext('2d');
            chart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: brands,
                    datasets: [{
                        label: 'Count',
                        data,
                        backgroundColor: ['#3b82f6', '#22c55e', '#f59e0b', '#a855f7', '#ef4444']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
                }
            });
        } else {
            chart.data.datasets[0].data = data;
            chart.update();
        }
    }

    async function fetchStats() {
        const res = await fetch('/api/stats/brands');
        if (!res.ok) throw new Error(`Stats failed: ${res.status}`);
        return await res.json();
    }

    async function refresh() {
        const stats = await fetchStats();
        setCounts(stats);
        ensureChart(stats);
    }

    btnUpdate.addEventListener('click', async () => {
        try {
            await refresh();
        } catch (e) {
            console.error(e);
        }
    });

    btnAdd.addEventListener('click', () => {
        const brand = (prompt('Brand (BMW/MERCEDES/TOYOTA/TESLA/HONDA):', 'BMW') || '').trim().toUpperCase();
        if (!brand) return;

        fetch(`/api/cars?brand=${encodeURIComponent(brand)}`, {
            method: 'POST'
        }).catch(console.error);
    });

    btnDelete.addEventListener('click', () => {
        const brand = (prompt('Remove one car by brand (BMW/MERCEDES/TOYOTA/TESLA/HONDA):', 'BMW') || '').trim().toUpperCase();
        if (!brand) return;

        fetch(`/api/cars?brand=${encodeURIComponent(brand)}`, { method: 'DELETE' }).catch(console.error);
    });

    const events = new EventSource('/api/events');
    events.addEventListener('statsUpdated', (ev) => {
        try {
            const stats = JSON.parse(ev.data);
            setCounts(stats);
            ensureChart(stats);
        } catch (e) {
            console.error('Bad event data', e, ev.data);
        }
    });
    events.onerror = (e) => console.warn('SSE error (server down?)', e);

    refresh().catch(console.error);
});
