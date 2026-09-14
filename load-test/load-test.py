import concurrent.futures
import time
import requests

BASE_URL = "http://localhost:8080"

CANDIDATE_IDS = range(1, 101)
EXAM_ID = 1


def test_candidate(candidate_id):
    device_id = f"load-test-device-{candidate_id}"

    try:
        start_url = (
            f"{BASE_URL}/api/sessions/start"
            f"?candidateId={candidate_id}"
            f"&examId={EXAM_ID}"
            f"&deviceId={device_id}"
        )

        response = requests.post(
            start_url,
            timeout=10
        )

        return {
            "candidate": candidate_id,
            "status": response.status_code,
            "response_time": response.elapsed.total_seconds(),
            "error": ""
        }

    except Exception as error:

        print(
            f"Candidate {candidate_id} ERROR: {error}"
        )

        return {
            "candidate": candidate_id,
            "status": "ERROR",
            "response_time": 0,
            "error": str(error)
        }


def main():

    print("Starting load test...")
    print("Simulating 100 concurrent candidates")
    print()

    start_time = time.time()

    with concurrent.futures.ThreadPoolExecutor(
            max_workers=100
    ) as executor:

        results = list(
            executor.map(
                test_candidate,
                CANDIDATE_IDS
            )
        )

    total_time = time.time() - start_time

    successful = sum(
        1
        for result in results
        if result["status"] == 200
    )

    failed = len(results) - successful

    response_times = [
        result["response_time"]
        for result in results
        if result["status"] != "ERROR"
    ]

    average_response_time = (
        sum(response_times) / len(response_times)
        if response_times
        else 0
    )

    print()
    print("========== LOAD TEST RESULT ==========")
    print(f"Total candidates: {len(results)}")
    print(f"Successful requests: {successful}")
    print(f"Failed requests: {failed}")
    print(f"Total execution time: {total_time:.2f} seconds")
    print(
        f"Average response time: "
        f"{average_response_time:.3f} seconds"
    )
    print("======================================")

    print()
    print("First 10 results:")

    for result in results[:10]:
        print(result)


if __name__ == "__main__":
    main()