import { describe, it, expect } from 'vitest';
const { sanitizeJavaHome } = require('../gradlew.js');

describe('sanitizeJavaHome', () => {
  it('handles null', () => {
    expect(sanitizeJavaHome(null)).toBeNull();
  });

  it('handles undefined', () => {
    expect(sanitizeJavaHome(undefined)).toBeNull();
  });

  it('handles empty string', () => {
    expect(sanitizeJavaHome('')).toBeNull();
  });

  it('trims whitespace', () => {
    expect(sanitizeJavaHome('  /opt/java  ')).toBe('/opt/java');
  });

  it('removes surrounding quotes', () => {
    expect(sanitizeJavaHome('"C:\\Program Files\\Java"')).toBe('C:\\Program Files\\Java');
  });

  it('removes trailing slashes and backslashes', () => {
    expect(sanitizeJavaHome('/opt/java/')).toBe('/opt/java');
    expect(sanitizeJavaHome('C:\\Program Files\\Java\\')).toBe('C:\\Program Files\\Java');
    expect(sanitizeJavaHome('/opt/java//')).toBe('/opt/java');
  });

  it('handles combination of quotes, whitespace, and trailing slashes', () => {
    expect(sanitizeJavaHome('  "/opt/java/"  ')).toBe('/opt/java');
  });

  it('preserves internal spaces and slashes', () => {
    expect(sanitizeJavaHome('/opt/my java/jdk')).toBe('/opt/my java/jdk');
  });
});
