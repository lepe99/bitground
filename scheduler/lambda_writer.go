package main

import (
	"context"
	"encoding/json"
	"fmt"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/lambda"
	"github.com/aws/aws-sdk-go-v2/service/lambda/types"
)

// lambdaWriter 쓰기 단계 Lambda(B)를 동기 호출한다 (운영의 수집 단계 A에서 사용).
// 권한: A의 실행 역할에 B에 대한 lambda:InvokeFunction만 준다
type lambdaWriter struct {
	client   *lambda.Client
	function string // B의 함수 이름 또는 ARN
}

func newLambdaWriter(ctx context.Context, function string) (lambdaWriter, error) {
	awsCfg, err := awsconfig.LoadDefaultConfig(ctx)
	if err != nil {
		return lambdaWriter{}, fmt.Errorf("AWS 설정 읽기 실패: %w", err)
	}
	return lambdaWriter{client: lambda.NewFromConfig(awsCfg), function: function}, nil
}

func (w lambdaWriter) Call(ctx context.Context, req WriterRequest) (WriterResponse, error) {
	payload, err := json.Marshal(req)
	if err != nil {
		return WriterResponse{}, err
	}
	out, err := w.client.Invoke(ctx, &lambda.InvokeInput{
		FunctionName:   &w.function,
		InvocationType: types.InvocationTypeRequestResponse,
		Payload:        payload,
	})
	if err != nil {
		return WriterResponse{}, fmt.Errorf("쓰기 Lambda 호출 실패: %w", err)
	}
	// 함수 안에서 에러를 반환·패닉하면 HTTP는 200이고 FunctionError에 표시된다
	if out.FunctionError != nil {
		return WriterResponse{}, fmt.Errorf("쓰기 Lambda 실행 실패 (%s): %s", *out.FunctionError, out.Payload)
	}
	var resp WriterResponse
	if err := json.Unmarshal(out.Payload, &resp); err != nil {
		return WriterResponse{}, fmt.Errorf("쓰기 Lambda 응답 해석 실패: %w", err)
	}
	return resp, nil
}
