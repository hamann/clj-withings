# Contributing to Withings Weight Tracker

Thank you for your interest in contributing! This project helps users sync their weight data across multiple fitness platforms.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Follow the setup instructions in the README
4. Make your changes
5. Test your changes
6. Submit a pull request

## Development Setup

1. Install Nix with flakes enabled
2. Run `nix develop` to enter the development shell
3. Copy `secrets.yaml.example` to `secrets.yaml` and configure your test credentials
4. Run tests with `bb test`
5. Run linting with `bb lint`

## Code Style

- Follow standard Clojure formatting
- Run `bb lint` before submitting
- Add tests for new functionality
- Update documentation for user-facing changes

## API Integration Guidelines

When adding new fitness platform integrations:

1. Check the platform's API terms of service
2. Follow OAuth2 best practices for authentication
3. Implement proper error handling and rate limiting
4. Add comprehensive tests
5. Update the README with setup instructions

## Submitting Changes

1. Create a feature branch from `main`
2. Make your changes with clear, descriptive commits
3. Update tests and documentation
4. Ensure `bb test` and `bb lint` pass
5. Submit a pull request with a clear description

## Questions?

Feel free to open an issue for questions or suggestions!
