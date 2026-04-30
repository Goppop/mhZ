/**
 * Build CSS path from el up to body (inclusive).
 * Each segment is tag+id+distinguishing classes.
 */
export function buildCssPath(el: Element): string[] {
  const path: string[] = []
  let current: Element | null = el
  while (current && current !== document.body.parentElement) {
    path.unshift(buildSegment(current))
    current = current.parentElement
  }
  return path
}

function buildSegment(el: Element): string {
  let seg = el.tagName.toLowerCase()
  if (el.id) {
    seg += '#' + el.id
  }
  // Include at most 2 classes to keep path stable but recognizable
  const classes = Array.from(el.classList).filter(
    (c) => !c.startsWith('__crawler_')
  )
  if (classes.length > 0) {
    seg += '.' + classes.slice(0, 2).join('.')
  }
  return seg
}

/**
 * Build sibling-index path from el up to body.
 * Helps the backend locate the element in the original HTML.
 */
export function buildIndexPath(el: Element): number[] {
  const path: number[] = []
  let current: Element | null = el
  while (current && current !== document.body.parentElement) {
    const parent: Element | null = current.parentElement
    if (!parent) break
    let idx = 0
    for (let i = 0; i < parent.children.length; i++) {
      if (parent.children[i] === current) {
        idx = i
        break
      }
    }
    path.unshift(idx)
    current = parent
  }
  return path
}

/**
 * Collect desired attributes from an element using the attribute whitelist.
 */
export function collectAttributes(el: Element): Record<string, string> {
  const attrs: Record<string, string> = {}
  const whitelist = ['href', 'src', 'title', 'alt', 'name', 'role', 'aria-label']
  for (const name of whitelist) {
    const val = el.getAttribute(name)
    if (val !== null) {
      attrs[name] = val
    }
  }
  // Collect data-* attributes
  for (const attr of Array.from(el.attributes)) {
    if (attr.name.startsWith('data-')) {
      attrs[attr.name] = attr.value
    }
  }
  return attrs
}

/**
 * Extract a short text summary from an element, collapsing whitespace and truncating.
 */
export function extractText(el: Element, maxLen = 200): string {
  const text = (el.textContent || '').replace(/\s+/g, ' ').trim()
  if (text.length > maxLen) {
    return text.slice(0, maxLen) + '…'
  }
  return text
}