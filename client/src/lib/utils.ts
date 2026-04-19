import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Copies text to clipboard with a fallback for non-secure contexts.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  if (!text) return false

  // Try navigator.clipboard first
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch (err) {
      console.warn("navigator.clipboard.writeText failed, falling back", err)
    }
  }

  // Fallback to document.execCommand('copy')
  try {
    const textArea = document.createElement("textarea")
    textArea.value = text
    
    // Ensure it's not visible but part of the DOM
    textArea.style.position = "fixed"
    textArea.style.left = "-9999px"
    textArea.style.top = "0"
    document.body.appendChild(textArea)
    
    textArea.focus()
    textArea.select()
    
    const successful = document.execCommand("copy")
    document.body.removeChild(textArea)
    return successful
  } catch (err) {
    console.error("Fallback copy failed", err)
    return false
  }
}
