
# MelExecuteResponse


## Properties

Name | Type
------------ | -------------
`status` | string
`output` | string
`combinedOutput` | string
`elapsedMs` | number
`totalElapsedMs` | number
`durationMicros` | number
`namespace` | string
`statementType` | string
`affectedIds` | Array&lt;string&gt;
`candidates` | [Array&lt;MelCandidateItem&gt;](MelCandidateItem.md)
`explainRows` | [Array&lt;MelExplainRow&gt;](MelExplainRow.md)
`funnel` | [FunnelTelemetry](FunnelTelemetry.md)
`funnelPhases` | [Array&lt;FunnelPhase&gt;](FunnelPhase.md)
`diagnostics` | [Array&lt;MelDiagnostic&gt;](MelDiagnostic.md)
`statements` | [Array&lt;MelStatementExecutionResult&gt;](MelStatementExecutionResult.md)

## Example

```typescript
import type { MelExecuteResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "status": null,
  "output": null,
  "combinedOutput": null,
  "elapsedMs": null,
  "totalElapsedMs": null,
  "durationMicros": null,
  "namespace": null,
  "statementType": null,
  "affectedIds": null,
  "candidates": null,
  "explainRows": null,
  "funnel": null,
  "funnelPhases": null,
  "diagnostics": null,
  "statements": null,
} satisfies MelExecuteResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MelExecuteResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


