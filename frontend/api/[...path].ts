declare const process: {
  env: Record<string, string | undefined>;
};

const HOP_BY_HOP_HEADERS = new Set([
  'connection',
  'content-encoding',
  'content-length',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
]);

export default {
  async fetch(request: Request): Promise<Response> {
    const configuredUpstream = process.env.API_UPSTREAM?.trim();
    if (!configuredUpstream) {
      return Response.json({ message: 'API_UPSTREAM is not configured for this Vercel deployment.' }, { status: 500 });
    }

    let upstream: URL;
    try {
      upstream = new URL(configuredUpstream);
    } catch {
      return Response.json({ message: 'API_UPSTREAM must be a valid backend URL.' }, { status: 500 });
    }

    if (upstream.protocol !== 'https:' && upstream.protocol !== 'http:') {
      return Response.json({ message: 'API_UPSTREAM must use HTTP or HTTPS.' }, { status: 500 });
    }

    const incoming = new URL(request.url);
    const target = new URL(`${incoming.pathname}${incoming.search}`, upstream.origin);
    const requestHeaders = new Headers(request.headers);
    for (const header of HOP_BY_HOP_HEADERS) requestHeaders.delete(header);
    requestHeaders.delete('host');

    try {
      const method = request.method.toUpperCase();
      const body = method === 'GET' || method === 'HEAD' ? undefined : await request.arrayBuffer();
      const upstreamResponse = await fetch(target, {
        method,
        headers: requestHeaders,
        body,
        redirect: 'manual',
      });

      const responseHeaders = new Headers();
      const connectionTokens = upstreamResponse.headers.get('connection')
        ?.split(',')
        .map((header) => header.trim().toLowerCase()) ?? [];
      upstreamResponse.headers.forEach((value, header) => {
        if (header !== 'set-cookie' && !HOP_BY_HOP_HEADERS.has(header) && !connectionTokens.includes(header)) {
          responseHeaders.append(header, value);
        }
      });
      for (const cookie of upstreamResponse.headers.getSetCookie?.() ?? []) {
        responseHeaders.append('set-cookie', cookie);
      }
      responseHeaders.set('cache-control', 'no-store');

      const emptyBodyStatus = [204, 205, 304].includes(upstreamResponse.status);
      return new Response(emptyBodyStatus ? null : upstreamResponse.body, {
        status: upstreamResponse.status,
        statusText: upstreamResponse.statusText,
        headers: responseHeaders,
      });
    } catch (error) {
      console.error('API_UPSTREAM request failed.', error);
      return Response.json({ message: 'The backend could not be reached.' }, { status: 502 });
    }
  },
};
