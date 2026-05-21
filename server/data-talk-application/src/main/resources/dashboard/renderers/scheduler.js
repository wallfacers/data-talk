(function() {
  'use strict';

  var cfgEl = document.getElementById('__BEZEL_CONFIG__');
  if (!cfgEl) return;
  var cfg = JSON.parse(cfgEl.textContent);

  var charts = {};
  var intervals = [];
  var paused = false;
  var widgetConfigs = cfg.widgets || [];
  // serverOrigin is injected into the config JSON at serve time (CSP-safe, no inline script).
  var origin = cfg.serverOrigin || window.__BEZEL_SERVER_ORIGIN__ || '';

  function post(type, payload) {
    if (window.parent && window.parent !== window) {
      try {
        window.parent.postMessage(
          Object.assign({ type: type }, payload || {}),
          '*'
        );
      } catch (e) {
        /* postMessage blocked by sandbox policy */
      }
    }
  }

  function initChart(widget) {
    var el = document.getElementById(widget.id);
    if (!el) {
      post('error', { widgetId: widget.id, message: 'element not found: ' + widget.id });
      return;
    }
    // ECharts must mount on the inner chart-container (sized by CSS), not the
    // outer widget box which also holds the title.
    var chartEl = document.getElementById(widget.id + '_chart') || el;

    var option = widget.baseOption || {};

    if (option.__needsMap && option.__needsMap.length > 0) {
      var mapNames = option.__needsMap;
      var pending = mapNames.length;
      var failed = false;

      for (var m = 0; m < mapNames.length; m++) {
        (function(mapName) {
          var url = origin + '/bezel/geo/' + encodeURIComponent(mapName) + '.json';
          var xhr = new XMLHttpRequest();
          xhr.open('GET', url, true);
          xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
              if (xhr.status >= 200 && xhr.status < 300) {
                try {
                  var geoJson = JSON.parse(xhr.responseText);
                  echarts.registerMap(mapName, geoJson);
                } catch (parseErr) {
                  failed = true;
                  el.innerHTML = '<div class="degradation-msg">地图数据加载失败</div>';
                  post('error', { widgetId: widget.id, message: 'geo json parse error for ' + mapName });
                }
              } else {
                failed = true;
                el.innerHTML = '<div class="degradation-msg">地图数据加载失败</div>';
                post('error', { widgetId: widget.id, message: 'geo fetch failed (' + xhr.status + ') for ' + mapName });
              }
              pending--;
              if (pending === 0 && !failed) {
                createChartInstance(widget, chartEl, option);
              }
            }
          };
          xhr.send();
        })(mapNames[m]);
      }
    } else {
      createChartInstance(widget, chartEl, option);
    }
  }

  function createChartInstance(widget, chartEl, option) {
    try {
      var cleanOption = shallowClone(option);
      delete cleanOption.__needsMap;

      var chart = echarts.init(chartEl);
      chart.setOption(cleanOption);
      charts[widget.id] = chart;
    } catch (err) {
      chartEl.innerHTML = '<div class="degradation-msg">图表初始化失败</div>';
      post('error', { widgetId: widget.id, message: err.message || String(err) });
    }
  }

  function shallowClone(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    var clone = {};
    for (var key in obj) {
      if (obj.hasOwnProperty(key)) {
        clone[key] = obj[key];
      }
    }
    return clone;
  }

  // The widget data endpoint is POST with body { params } (CSP/CORS contract
  // covered by the null-origin preflight); a GET would 405 and never load data.
  function fetchWidget(widget) {
    if (!widget.endpoint) return;
    var xhr = new XMLHttpRequest();
    xhr.open('POST', widget.endpoint, true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.onreadystatechange = function() {
      if (xhr.readyState === 4 && xhr.status >= 200 && xhr.status < 300) {
        try {
          applyWidgetData(widget, JSON.parse(xhr.responseText));
        } catch (e) {
          post('error', { widgetId: widget.id, message: 'poll parse error: ' + e.message });
        }
      } else if (xhr.readyState === 4) {
        post('error', { widgetId: widget.id, message: 'poll failed (' + xhr.status + ')' });
      }
    };
    xhr.send(JSON.stringify({ params: widget.params || {} }));
  }

  function startPolling(widget) {
    if (!widget.hasData) return;
    if (!widget.intervalMs || widget.intervalMs <= 0) return;
    if (!widget.endpoint) return;

    var intervalId = setInterval(function() {
      if (paused) return;
      fetchWidget(widget);
    }, widget.intervalMs);

    intervals.push(intervalId);
  }

  // The widget data endpoint returns { columns: string[], rows: any[][], executedAt }.
  function applyWidgetData(widget, data) {
    var el = document.getElementById(widget.id);
    if (!el) return;
    if (!data || !data.columns || !data.rows) return;

    if (widget.type === 'chart') {
      var chart = charts[widget.id];
      if (!chart) return;
      try {
        // Merge only the dataset so the first-screen baseOption (axes/series) is preserved.
        // ECharts dataset.source = [headerRow, ...dataRows].
        var source = [data.columns].concat(data.rows);
        chart.setOption({ dataset: { source: source } }, { lazyUpdate: true });
      } catch (err) {
        post('error', { widgetId: widget.id, message: 'setOption error: ' + err.message });
      }
    } else {
      applyHtmlData(widget.type, el, data.columns, data.rows);
    }
  }

  function colIndex(columns, name) {
    for (var i = 0; i < columns.length; i++) {
      if (String(columns[i]).toLowerCase() === name) return i;
    }
    return -1;
  }

  function applyHtmlData(type, el, columns, rows) {
    if (type === 'kpi') {
      if (!rows.length) return;
      var row = rows[0];
      var vi = colIndex(columns, 'value');
      var valueEl = el.querySelector('.kpi-value');
      if (valueEl) valueEl.textContent = String(row[vi >= 0 ? vi : 0]);
      var ti = colIndex(columns, 'trend');
      if (ti >= 0 && valueEl) {
        var trend = String(row[ti]).toLowerCase();
        valueEl.classList.remove('kpi-change-up', 'kpi-change-down');
        if (trend === 'up') valueEl.classList.add('kpi-change-up');
        else if (trend === 'down') valueEl.classList.add('kpi-change-down');
      }
    } else if (type === 'table') {
      var body = el.querySelector('.widget-body') || el;
      var html = '<table class="widget-table"><thead><tr>';
      for (var c = 0; c < columns.length; c++) html += '<th>' + escHtml(columns[c]) + '</th>';
      html += '</tr></thead><tbody>';
      for (var r = 0; r < rows.length; r++) {
        html += '<tr>';
        for (var k = 0; k < rows[r].length; k++) html += '<td>' + escHtml(rows[r][k]) + '</td>';
        html += '</tr>';
      }
      html += '</tbody></table>';
      body.innerHTML = html;
    }
    // markdown / section / divider / image / filter are static — nothing to apply.
  }

  function escHtml(v) {
    if (v === null || v === undefined) return '';
    return String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function handleWidgetUpdate(msg) {
    var widgetId = msg.widgetId;
    var el = document.getElementById(widgetId);
    if (!el) return;

    if (msg.baseOption) {
      var chart = charts[widgetId];
      if (chart) {
        try {
          // Incremental update carries a fresh, fully-merged baseOption — replace, don't merge.
          chart.setOption(msg.baseOption, true);
        } catch (err) {
          post('error', { widgetId: widgetId, message: 'widget update setOption error: ' + err.message });
        }
      }
    }

    if (msg.html) {
      el.innerHTML = msg.html;
    }
  }

  function handleParamsUpdate(msg) {
    var params = msg.params;
    if (!params) return;
    for (var i = 0; i < widgetConfigs.length; i++) {
      widgetConfigs[i].params = params;
    }
  }

  function pauseAll() {
    paused = true;
  }

  function resumeAll() {
    paused = false;
  }

  function resizeAll() {
    for (var id in charts) {
      if (charts.hasOwnProperty(id)) {
        try {
          charts[id].resize();
        } catch (e) {
          /* resize error, ignore */
        }
      }
    }
  }

  function init() {
    for (var i = 0; i < widgetConfigs.length; i++) {
      var widget = widgetConfigs[i];
      try {
        if (widget.type === 'chart') {
          initChart(widget);
        }
        // Fetch once immediately so the first paint shows data instead of a
        // skeleton until the first poll interval elapses.
        if (widget.hasData) {
          fetchWidget(widget);
        }
        startPolling(widget);
      } catch (err) {
        var el = document.getElementById(widget.id);
        if (el) {
          el.innerHTML = '<div class="degradation-msg">组件初始化失败</div>';
        }
        post('error', { widgetId: widget.id, message: err.message || String(err) });
      }
    }

    window.addEventListener('resize', function() {
      resizeAll();
    });

    window.addEventListener('message', function(evt) {
      var data = evt.data;
      if (!data || !data.type) return;

      switch (data.type) {
        case 'widget/update':
          handleWidgetUpdate(data);
          break;
        case 'params/update':
          handleParamsUpdate(data);
          break;
        case 'refresh/pause':
          pauseAll();
          break;
        case 'refresh/resume':
          resumeAll();
          break;
      }
    });

    post('ready', { jsonHash: cfg.jsonHash });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
