
# MelStatementExecutionResult


## Properties

Name | Type
------------ | -------------
`statementType` | string
`status` | string
`output` | string
`elapsedMs` | number
`durationMicros` | number
`affectedIds` | Array&lt;string&gt;
`candidates` | [Array&lt;MelCandidateItem&gt;](MelCandidateItem.md)
`explainRows` | [Array&lt;MelExplainRow&gt;](MelExplainRow.md)
`funnel` | [FunnelTelemetry](FunnelTelemetry.md)
`funnelPhases` | [Array&lt;FunnelPhase&gt;](FunnelPhase.md)
`diagnostics` | [Array&lt;MelDiagnostic&gt;](MelDiagnostic.md)

## Example

```typescript
import type { MelStatementExecutionResult } from ''

// TODO: Update the object below with actual values
const example = {
  "statementType": null,
  "status": null,
  "output": null,
  "elapsedMs": null,
  "durationMicros": null,
  "affectedIds": null,
  "candidates": null,
  "explainRows": null,
  "funnel": null,
  "funnelPhases": null,
  "diagnostics": null,
} satisfies MelStatementExecutionResult

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MelStatementExecutionResult
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


