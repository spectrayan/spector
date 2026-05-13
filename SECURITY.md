# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.1.x   | :white_check_mark: |

## Reporting a Vulnerability

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to: **security@spectrayan.com**

Please include:

- A description of the vulnerability
- Steps to reproduce (if applicable)
- Potential impact assessment
- Any suggested fixes

### Response Timeline

- **Acknowledgment:** Within 48 hours
- **Initial assessment:** Within 5 business days
- **Fix release:** Depends on severity, typically within 30 days

### What to Expect

- You will receive an acknowledgment of your report
- We will investigate and validate the vulnerability
- We will work on a fix and coordinate disclosure
- You will be credited in the security advisory (unless you prefer anonymity)

## Security Best Practices for Users

- Always use the latest release version
- Run the JVM with appropriate security manager settings in production
- Do not expose the REST API to the public internet without authentication
- Review memory-mapped file permissions on the host filesystem
