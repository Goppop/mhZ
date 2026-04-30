/**
 * Remove <script> tags and on* event attributes from HTML.
 * Also neutralizes <a href="javascript:..."> to prevent JS execution.
 */
export function sanitizeHtml(html: string): string {
  let result = html

  // Remove <script> tags and their content
  result = result.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')

  // Remove inline event handlers (onclick, onmouseover, etc.)
  result = result.replace(/\s+on\w+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>/]+)/gi, '')

  // Neutralize javascript: URLs in href
  result = result.replace(/href\s*=\s*(?:"javascript:[^"]*"|'javascript:[^']*')/gi, 'href="#"')

  // Remove <base> tags to prevent relative URL rebasing
  result = result.replace(/<base\b[^>]*>/gi, '')

  return result
}