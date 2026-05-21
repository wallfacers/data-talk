# Third-Party Licenses

DataTalk bundles and depends on third-party open-source software. This file
collects the license notices required for redistribution. We are grateful to
the maintainers and communities behind these projects.

---

## OpenCode

DataTalk uses [OpenCode](https://github.com/anomalyco/opencode) as its AI agent
runtime. At runtime the backend resolves the OpenCode binary (and runs
`opencode serve` as a managed local subprocess) and communicates with it over
HTTP (Streamable HTTP + SSE / JSON-RPC). The `@opencode-ai/plugin` npm package
is also used.

- Project: https://github.com/anomalyco/opencode
- License: MIT

```
MIT License

Copyright (c) 2025 opencode

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

> Other bundled dependencies (Java, Node.js, and Rust packages) retain their
> respective licenses as declared in their distribution metadata
> (`pom.xml`, `package.json` / `pnpm-lock.yaml`, `Cargo.toml` / `Cargo.lock`).
