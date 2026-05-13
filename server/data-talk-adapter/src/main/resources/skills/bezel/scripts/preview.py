#!/usr/bin/env python3
"""bezel local preview helper.

Reads a dashboard.html + a mock-data JSON, opens the HTML in a browser with
mock data inlined (replaces fetch() with a stub that returns mock rows).
For development only; not used in production.
"""
from __future__ import annotations
import argparse
import json
import sys
import webbrowser
from pathlib import Path

def main() -> int:
    ap = argparse.ArgumentParser(description="Preview a bezel dashboard.html locally with mock data")
    ap.add_argument("html", type=Path, help="path to dashboard.html")
    ap.add_argument("--mock", type=Path, default=None,
                    help="path to mock data JSON: { widgetId: { columns, rows } }")
    args = ap.parse_args()

    html = args.html.read_text(encoding="utf-8")
    if args.mock:
        mock = json.loads(args.mock.read_text(encoding="utf-8"))
        stub = (
            "<script>(function(){const M=" + json.dumps(mock) + ";"
            "const _f=window.fetch;window.fetch=function(u,opts){"
            "const m=u.match(/\\/widgets\\/([^/]+)\\/data/);"
            "if(m&&M[m[1]])return Promise.resolve({ok:true,status:200,"
            "json:()=>Promise.resolve(M[m[1]])});return _f(u,opts);};})();</script>"
        )
        html = html.replace("</head>", stub + "</head>", 1)

    out = args.html.with_suffix(".preview.html")
    out.write_text(html, encoding="utf-8")
    print(f"[bezel.preview] wrote {out}")
    webbrowser.open(out.as_uri())
    return 0

if __name__ == "__main__":
    sys.exit(main())
