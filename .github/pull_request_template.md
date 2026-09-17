## Description
<!-- Describe your changes in detail -->
<!-- Include motivation and context if it's a new feature -->

## Related Issue
<!-- Link to the issue here: "Closes #123" -->

## Architecture & Discussions
<!-- Architectural changes require an accepted ADR and prior RFC discussion -->
- **Implements ADR**: ADR-____ (or `N/A`)
- **Discussion / RFC**: #____ (or `N/A`)

## Type of Change
<!-- Check the relevant option -->
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Performance improvement (change that improves throughput or latency)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Module(s) Affected
<!-- Check all that apply -->
- [ ] `nucleus/*` (Foundation, SPIs, SIMD, GPU, Index, Events)
- [ ] `memory/*` (Cognitive Memory, Ingestion, Providers, Metrics)
- [ ] `synapse/*` (Runtime, Synapse Gateway, MCP, Connectors, CLI, Client, Spring)
- [ ] `bench/*` (Benchmarks & Evaluations)
- [ ] `docs/*` / Root Configuration

## Checklist
- [ ] My commits include a valid DCO 1.1 sign-off (`git commit -s`)
- [ ] License headers are verified and formatted (`mvn license:format`)
- [ ] My code follows the code style of this project
- [ ] I have added Javadoc for all public classes/methods
- [ ] I have added tests to cover my changes
- [ ] All new and existing tests passed (`mvn test`)
- [ ] No hardcoded secrets or credentials are included
- [ ] JMH benchmark results included (if performance-related)
